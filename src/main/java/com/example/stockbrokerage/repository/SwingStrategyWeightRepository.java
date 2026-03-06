package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.SwingStrategyWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SwingStrategyWeightRepository extends JpaRepository<SwingStrategyWeight, Long> {

    Optional<SwingStrategyWeight> findByStrategyName(String strategyName);
}
