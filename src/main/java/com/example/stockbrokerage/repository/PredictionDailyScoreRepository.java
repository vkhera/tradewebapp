package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.PredictionDailyScore;
import com.example.stockbrokerage.entity.PredictionDailyScore.PredictionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionDailyScoreRepository extends JpaRepository<PredictionDailyScore, Long> {

    /** One row per (scoreDate, predictionType) pair. Used for upsert during scoring. */
    Optional<PredictionDailyScore> findByScoreDateAndPredictionType(LocalDate scoreDate, PredictionType predictionType);

    /** All scores (all types) for days strictly after the cutoff, ordered chronologically. */
    List<PredictionDailyScore> findByScoreDateAfterOrderByScoreDateAsc(LocalDate after);
}
