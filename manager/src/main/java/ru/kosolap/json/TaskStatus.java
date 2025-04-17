package ru.kosolap.json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@ToString
@AllArgsConstructor
@JsonIgnoreProperties("startTime")
public class TaskStatus {
    private TaskStatusEnum status;
    private List<String> answer;
    private Instant startTime;
    private ConcurrentHashMap<Integer, Double> progressMap; // partNumber → progress (0-100)
    private double totalProgress;

    public TaskStatus() {
        this.status = TaskStatusEnum.IN_PROGRESS;
        this.answer = new ArrayList<>();
        this.startTime = Instant.now();
        this.progressMap = new ConcurrentHashMap<>();
        this.totalProgress = 0.0;
    }

    public void updateProgress(int partNumber, double progress) {
        progressMap.put(partNumber, progress);
        calculateOverallProgress();
    }

    public void setProgressMap(ConcurrentHashMap<Integer, Double> progressMap) {
    this.progressMap = progressMap;
   }

   private void calculateOverallProgress() {
        int totalParts;
        try {
            totalParts = Integer.parseInt(System.getenv().getOrDefault("HASH_PARTS", "3"));
            
            double sum = 0.0;
            for (int i = 0; i < totalParts; i++) {
                double partProgress = progressMap.getOrDefault(i, 0.0);
                sum += partProgress;
            }
            
            double average = sum / totalParts;

            BigDecimal rounded = new BigDecimal(average).setScale(2, RoundingMode.HALF_UP);
            totalProgress = rounded.doubleValue();
            
        } catch (NumberFormatException e) {
            System.err.println("[ERROR] Invalid HASH_PARTS value: " + System.getenv("HASH_PARTS"));
            totalProgress = 0.0;
        }
    }

    @JsonIgnore
    public boolean isCompleted() {
        System.out.println("Точно готово!");
        return progressMap.values().stream().allMatch(p -> p == 100.0);
    }
}
