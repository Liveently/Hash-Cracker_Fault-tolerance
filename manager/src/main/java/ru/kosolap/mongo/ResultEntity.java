package ru.kosolap.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;


@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "results")
@CompoundIndex(def = "{'requestId': 1, 'partNumber': 1}")
public class ResultEntity {
    @Id
    private String id;

    private String requestId;
    private int partNumber;
    private double progress;
    private String answer;
}
