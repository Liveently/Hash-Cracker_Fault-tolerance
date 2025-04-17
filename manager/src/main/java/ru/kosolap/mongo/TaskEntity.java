package ru.kosolap.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import ru.kosolap.json.TaskStatusEnum;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tasks")
public class TaskEntity {
    @Id
    private String requestId;
    private String hash;
    private Integer maxLength;
    private TaskStatusEnum status;
    private Instant createdAt;
    private List<String> answer;
    private double totalProgress;
    private int partCount;
}
