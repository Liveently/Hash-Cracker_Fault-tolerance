package ru.kosolap.service;

import jakarta.xml.bind.DatatypeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.paukov.combinatorics.Generator;
import org.springframework.web.client.RestTemplate;
import org.paukov.combinatorics.CombinatoricsFactory;
import org.paukov.combinatorics.ICombinatoricsVector;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.lang.Math;
import java.util.List;


import ru.kosolap.json.CrackHashManagerRequest;
import ru.kosolap.json.CrackHashWorkerResponse;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.kosolap.config.RabbitConfig;

@Service
public class WorkerService {

   @Autowired
    private RabbitTemplate rabbitTemplate;

    private final Duration taskTimeout = Duration.parse(System.getenv().getOrDefault("TASK_TIMEOUT", "PT5M"));

    @RabbitListener(queues = "task_queue")
    public void receiveTask(CrackHashManagerRequest body) {
        crackHashTask(body);
    }

    @Async
    public void crackHashTask(CrackHashManagerRequest body) { 
        System.out.println("Received task: " + body.getRequestId());


            System.out.println("=== Received task ===");
    System.out.println("Request ID: " + body.getRequestId());
    System.out.println("Hash: " + body.getHash());
    System.out.println("Alphabet: " + body.getAlphabet().getSymbols());
    System.out.println("Max length: " + body.getMaxLength());
    System.out.println("Part number: " + body.getPartNumber() + " of " + body.getPartCount());

        String ALPHABET = String.join("", body.getAlphabet().getSymbols());
        int POSITIONS_NUM = body.getMaxLength();
        int ALL_PARTS_NUM = body.getPartCount();
        int MY_PART_IDX = body.getPartNumber();
        byte[] HASH = DatatypeConverter.parseHexBinary(body.getHash());

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        ICombinatoricsVector<String> vector = CombinatoricsFactory.createVector(ALPHABET.split(""));
        Generator<String> gen = CombinatoricsFactory.createPermutationWithRepetitionGenerator(vector, POSITIONS_NUM);

        long idx = 0;
        long totalPermutations = (long) gen.getNumberOfGeneratedObjects(); 
        Instant startTime = Instant.now();

        System.out.println("Start count permutations...");

        for (ICombinatoricsVector<String> perm : gen) {
            if (idx % ALL_PARTS_NUM == MY_PART_IDX) {
                String str = String.join("", perm.getVector());
                byte[] combHash = md5.digest(str.getBytes());

                if (Arrays.equals(combHash, HASH)) {
                    System.out.println("I found hash: " + str);

                    double progress = (idx / (double) totalPermutations) * 100;
                    progress = Math.round(progress * 100.0) / 100.0;

                    sendAnswer(body.getRequestId(), str, progress, body.getPartNumber()); 
                }
            }

            double progress = (idx / (double) totalPermutations) * 100;
            progress = Math.round(progress * 100.0) / 100.0;


            int progressUpdateInterval = Integer.parseInt(System.getenv().getOrDefault("PROGRESS_UPDATE_INTERVAL", "10000"));

            if (idx % progressUpdateInterval == 0) {
                sendProgress(body.getRequestId(), progress, body.getPartNumber());
            }

            Duration dur = Duration.between(startTime, Instant.now());
            if (dur.toMillis() > taskTimeout.toMillis()) {
                System.out.println("Exceeded time limit: exiting");
                sendProgress(body.getRequestId(), progress, body.getPartNumber());
                return;
            }

            idx++;
        }

        System.out.println("End count permutations...");
        sendProgress(body.getRequestId(), 100.0, body.getPartNumber());
    }

    private void sendAnswer(String id, String answer, double progress, int partNumber) { 

        CrackHashWorkerResponse response = new CrackHashWorkerResponse();
        response.setRequestId(id);
        response.setAnswers(new CrackHashWorkerResponse.Answers());
        response.getAnswers().getWords().add(answer);
        response.setProgress(progress); 
        response.setPartNumber(partNumber);

        System.out.println(" [>] Sending result: " + response);
        rabbitTemplate.convertAndSend("result_queue", response);
    }



    private void sendProgress(String id, double progress, int partNumber) {
    CrackHashWorkerResponse response = new CrackHashWorkerResponse();
    response.setRequestId(id);
    response.setProgress(progress);
    response.setPartNumber(partNumber);

    CrackHashWorkerResponse.Answers answers = new CrackHashWorkerResponse.Answers();
    answers.setWords(List.of("")); 
    response.setAnswers(answers);

    System.out.println(" [>] Sending progress: " + response);
    rabbitTemplate.convertAndSend("progress_queue", response);
}


    
            

}

