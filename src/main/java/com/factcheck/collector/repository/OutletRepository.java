package com.factcheck.collector.repository;

import com.factcheck.collector.domain.entity.Outlet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutletRepository extends JpaRepository<Outlet, Long> {
    Optional<Outlet> findByNameIgnoreCase(String name);
}
