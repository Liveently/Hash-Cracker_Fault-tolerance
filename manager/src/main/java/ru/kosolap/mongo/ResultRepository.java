package ru.kosolap.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ResultRepository extends MongoRepository<ResultEntity, String> {
    List<ResultEntity> findAllByRequestId(String requestId);
    
    Optional<ResultEntity> findByRequestIdAndPartNumber(String requestId, Integer partNumber);
    Optional<ResultEntity> findByRequestIdAndPartNumberAndAnswerIsNotNull(String requestId, int partNumber);
}