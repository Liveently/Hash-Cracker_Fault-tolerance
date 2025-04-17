package ru.kosolap.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.security.auth.message.callback.PrivateKeyCallback.Request;
import ru.kosolap.json.CrackHashManagerRequest;
import ru.kosolap.json.CrackHashWorkerResponse;
import ru.kosolap.json.HashAndLength;
import ru.kosolap.json.RequestId;
import ru.kosolap.json.TaskStatus;
import ru.kosolap.json.PartNumber;
import ru.kosolap.json.Progress;
import ru.kosolap.json.TaskStatusEnum;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import ru.kosolap.config.RabbitConfig;

import ru.kosolap.mongo.TaskRepository;
import ru.kosolap.mongo.ResultRepository;
import ru.kosolap.mongo.ResultEntity;
import ru.kosolap.mongo.TaskEntity;
import javax.annotation.PostConstruct;  
import java.util.Optional;  // Добавьте эту строку в начало файла


@Service
public class ManagerService {

    private final TaskRepository taskRepository;
    private final ResultRepository resultRepository;

    private ConcurrentHashMap<RequestId, TaskStatus> idAndStatus; 
    
    private final String LETTERS_AND_DIGITS = System.getenv().getOrDefault("LETTERS_AND_DIGITS", "abcdefghijklmnopqrstuvwxyz0123456789");
    private final Duration taskTimeout = Duration.parse(System.getenv().getOrDefault("TASK_TIMEOUT", "PT5M") );
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public ManagerService(TaskRepository taskRepository, ResultRepository resultRepository, RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.taskRepository = taskRepository;
        this.resultRepository = resultRepository;
        this.idAndStatus = new ConcurrentHashMap<RequestId, TaskStatus>();
    }

     // Слушатель для обработки сообщений из очереди progress_queue
    @RabbitListener(queues = "progress_queue")
    public void handleProgress(CrackHashWorkerResponse response) {
        System.out.println("Получен прогресс от воркера: " + response);
        updateProgress(response);
    }

    // Слушатель для обработки сообщений из очереди result_queue
    @RabbitListener(queues = "result_queue")
    public void handleResult(CrackHashWorkerResponse response) {
        System.out.println("Получен ответ от воркера: " + response);
        recieveAnswer(response);
    }



@PostConstruct
public void recoverFromMongo() {
    List<TaskEntity> tasks = taskRepository.findAll();
    System.out.println("[RECOVERY] Найдено задач в MongoDB: " + tasks.size());

    for (TaskEntity task : tasks) {
        RequestId requestId = new RequestId(task.getRequestId());
        System.out.println("[RECOVERY] Восстановление задачи: " + task.getRequestId() + 
                         ", статус: " + task.getStatus());

        // Создаем статус задачи
        TaskStatus taskStatus = new TaskStatus();
        taskStatus.setStatus(task.getStatus());
        taskStatus.setAnswer(new ArrayList<>(task.getAnswer()));
        
        // Явно создаем ConcurrentHashMap с указанием типов
        ConcurrentHashMap<Integer, Double> progressMap = new ConcurrentHashMap<>();
        taskStatus.setProgressMap(progressMap);

        // Восстанавливаем прогресс
        List<ResultEntity> results = resultRepository.findAllByRequestId(task.getRequestId());
        System.out.println("[RECOVERY] Найдено результатов для задачи: " + results.size());

        for (ResultEntity result : results) {
            progressMap.put(result.getPartNumber(), result.getProgress());
        }


        if (!progressMap.isEmpty()) {
            double totalProgress = progressMap.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    
                double roundedProgress = Math.round(totalProgress * 100.0) / 100.0;
                taskStatus.setTotalProgress(roundedProgress);
        }

        idAndStatus.put(requestId, taskStatus);
        System.out.println("[RECOVERY] Задача восстановлена: " + requestId + 
                          ", ответов: " + taskStatus.getAnswer().size() + 
                          ", прогресс: " + taskStatus.getTotalProgress());
    }
    System.out.println("[RECOVERY] Все задачи восстановлены из MongoDB. Всего: " + idAndStatus.size());
}



    private CrackHashManagerRequest.Alphabet initAlphabet() { 
        CrackHashManagerRequest.Alphabet alphabet = new CrackHashManagerRequest.Alphabet();

        for (String charString : LETTERS_AND_DIGITS.split("")) {
            alphabet.getSymbols().add(charString);
        }

        return alphabet;
    }

    public RequestId getRequestId(HashAndLength body) { //отправляет задачу воркерам

        System.out.println("Received Hash: " + body.getHash() + ", Length: " + body.getMaxLength());

        RequestId requestId = new RequestId(UUID.randomUUID().toString());

        TaskEntity task = new TaskEntity();
        task.setRequestId(requestId.getRequestId());
        task.setHash(body.getHash());
        task.setMaxLength(body.getMaxLength());
        task.setStatus(TaskStatusEnum.IN_PROGRESS);
        task.setCreatedAt(Instant.now());
        task.setAnswer(new ArrayList<>());
        task.setTotalProgress(0.0);
        taskRepository.save(task);  // Сохраняем задачу

        idAndStatus.put(requestId, new TaskStatus()); // Статус автоматически будет IN_PROGRESS

        CrackHashManagerRequest request = new CrackHashManagerRequest();

        request.setRequestId(requestId.getRequestId());
        request.setHash(body.getHash());
        request.setMaxLength(body.getMaxLength());
        request.setAlphabet(initAlphabet());

 

        int partCount = Integer.parseInt( System.getenv().getOrDefault("HASH_PARTS", "4") );
        request.setPartCount(partCount);


        for (int part = 0; part < partCount; part++) {
            request.setPartNumber(part);

            try {
            rabbitTemplate.convertAndSend(
                RabbitConfig.TASK_EXCHANGE,
                RabbitConfig.ROUTING_KEY,
                request
            );
            System.out.println("Request part " + part + " sent successfully");
        } catch (Exception e) {
            // Логирование ошибки при отправке сообщения
            System.out.println("Failed to send part " + part + " to RabbitMQ");
            e.printStackTrace();  // Выводим трассировку стека ошибки
        }
        }

        return requestId;
    }

    public TaskStatus getTaskStatus(RequestId id) { 

        TaskStatus status = idAndStatus.get(id);
    
        Duration dur = Duration.between(status.getStartTime(), Instant.now()); 
        
        if (dur.toMillis() > taskTimeout.toMillis()) {
        if (status.getAnswer().isEmpty()) {
            status.setStatus(TaskStatusEnum.TIMEOUT); // Если ответов нет, таймаут
        } else  {
            // Если есть частичный результат, но не завершена за таймаут
            status.setStatus(TaskStatusEnum.PARTIAL_SUCCESS_TIMEOUT);
        }}

        if (status.isCompleted()) {
            if (status.getAnswer().isEmpty()) {
                // Если все части завершены, но нет ответа, статус NO_RESULTS
                status.setStatus(TaskStatusEnum.NO_RESULTS);
            } else {
                // Если все части завершены и есть ответ, статус SUCCESS
                status.setStatus(TaskStatusEnum.SUCCESS);
            }
        } 

        return status;
    }




public void recieveAnswer(CrackHashWorkerResponse response) {
    if (response.getAnswers().getWords().isEmpty()) {
        return;
    }

    String answer = response.getAnswers().getWords().get(0);
    RequestId requestId = new RequestId(response.getRequestId());
    TaskStatus status = idAndStatus.get(requestId);

    if (status == null) {
        return;
    }

    synchronized (status) {
        // Проверка на дубликаты
        if (status.getAnswer().contains(answer)) {
            return;
        }

        // Сохраняем результат
        ResultEntity result = new ResultEntity();
        result.setId(UUID.randomUUID().toString());
        result.setRequestId(response.getRequestId());
        result.setPartNumber(response.getPartNumber());
        result.setAnswer(answer);
        result.setProgress(response.getProgress()); // При нахождении ответа устанавливаем 100%
        resultRepository.save(result);

        // Обновляем статус
        status.getAnswer().add(answer);
        status.updateProgress(response.getPartNumber(), response.getProgress()); // Прогресс части = 100%
        status.setStatus(calculateCurrentStatus(status));

        // Обновляем задачу в MongoDB
        updateTaskEntity(response.getRequestId(), status);
    }
}

public void updateProgress(CrackHashWorkerResponse response) {
    RequestId requestId = new RequestId(response.getRequestId());
    TaskStatus status = idAndStatus.get(requestId);
    
    if (status == null) {
        return;
    }
    
    synchronized (status) {

        // Обновляем прогресс
        status.updateProgress(response.getPartNumber(), response.getProgress());
        status.setStatus(calculateCurrentStatus(status));

        // Сохраняем/обновляем запись прогресса
        ResultEntity progressEntity = resultRepository
            .findByRequestIdAndPartNumber(response.getRequestId(), response.getPartNumber())
            .orElseGet(() -> createNewProgressEntity(response));
        
        progressEntity.setProgress(response.getProgress());
        resultRepository.save(progressEntity);

        // Обновляем задачу
        updateTaskEntity(response.getRequestId(), status);
    }
}

private void updateTaskEntity(String requestId, TaskStatus status) {
    TaskEntity task = taskRepository.findById(requestId).orElseThrow();
    task.setAnswer(new ArrayList<>(status.getAnswer()));
    task.setTotalProgress(status.getTotalProgress());
    task.setStatus(status.getStatus());
    taskRepository.save(task);
}

private ResultEntity createNewProgressEntity(CrackHashWorkerResponse response) {
    ResultEntity entity = new ResultEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setRequestId(response.getRequestId());
    entity.setPartNumber(response.getPartNumber());
    return entity;
}

private TaskStatusEnum calculateCurrentStatus(TaskStatus status) {
    if (status.isCompleted()) {
        return status.getAnswer().isEmpty() ? 
            TaskStatusEnum.NO_RESULTS : 
            TaskStatusEnum.SUCCESS;
    }
    return status.getAnswer().isEmpty() ? 
        TaskStatusEnum.IN_PROGRESS : 
        TaskStatusEnum.PARTIAL_SUCCESS_WORKING;
}

}
