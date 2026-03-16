package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.SystemHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemHeartbeatRepository extends JpaRepository<SystemHeartbeat, Long> {
}
