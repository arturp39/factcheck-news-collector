package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findByEnabledTrue();

    List<Source> findByEnabledTrueAndType(SourceType type);
}
