package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.Rule;
import com.example.stockbrokerage.entity.Rule.RuleLevel;
import com.example.stockbrokerage.entity.Rule.RuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {
    
    List<Rule> findByActiveTrue();
    
    List<Rule> findByRuleType(RuleType ruleType);
    
    List<Rule> findByLevel(RuleLevel level);
    
    List<Rule> findByLevelAndActiveTrue(RuleLevel level);
    
    List<Rule> findByClientIdAndActiveTrue(Long clientId);
    
    List<Rule> findByRuleTypeAndActiveTrue(RuleType ruleType);
}
