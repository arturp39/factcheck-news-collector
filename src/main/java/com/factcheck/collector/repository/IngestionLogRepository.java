package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.IngestionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngestionLogRepository extends JpaRepository<IngestionLog, Long> {

    Optional<IngestionLog> findByCorrelationId(String correlationId);
}
