package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.RuleRequest;
import com.example.stockbrokerage.entity.Rule;
import com.example.stockbrokerage.entity.Rule.RuleLevel;
import com.example.stockbrokerage.entity.Rule.RuleType;
import com.example.stockbrokerage.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleService {
    
    private final RuleRepository ruleRepository;
    private final AuditService auditService;
    
    @Transactional
    @CacheEvict(value = "rules", allEntries = true)
    public Rule createRule(RuleRequest request, String createdBy) {
        Rule rule = Rule.builder()
            .ruleName(request.getRuleName())
            .description(request.getDescription())
            .ruleType(request.getRuleType())
            .level(request.getLevel())
            .clientId(request.getClientId())
            .ruleContent(request.getRuleContent())
            .active(request.getActive())
            .priority(request.getPriority())
            .createdBy(createdBy)
            .build();
        
        Rule saved = ruleRepository.save(rule);
        auditService.logRuleEvent(saved.getId(), "CREATE", createdBy, "Rule created: " + saved.getRuleName());
        log.info("Rule created: {}", saved.getRuleName());
        
        return saved;
    }
    
    @Cacheable(value = "rules", key = "#id")
    public Rule getRuleById(Long id) {
        return ruleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Rule not found with id: " + id));
    }
    
    public List<Rule> getAllRules() {
        return ruleRepository.findAll();
    }
    
    public List<Rule> getActiveRules() {
        return ruleRepository.findByActiveTrue();
    }
    
    public List<Rule> getRulesByType(RuleType ruleType) {
        return ruleRepository.findByRuleType(ruleType);
    }
    
    public List<Rule> getRulesByLevel(RuleLevel level) {
        return ruleRepository.findByLevel(level);
    }
    
    @Transactional
    @CacheEvict(value = "rules", allEntries = true)
    public Rule updateRule(Long id, RuleRequest request, String updatedBy) {
        Rule rule = getRuleById(id);
        
        rule.setRuleName(request.getRuleName());
        rule.setDescription(request.getDescription());
        rule.setRuleType(request.getRuleType());
        rule.setLevel(request.getLevel());
        rule.setClientId(request.getClientId());
        rule.setRuleContent(request.getRuleContent());
        rule.setActive(request.getActive());
        rule.setPriority(request.getPriority());
        
        Rule updated = ruleRepository.save(rule);
        auditService.logRuleEvent(id, "UPDATE", updatedBy, "Rule updated: " + updated.getRuleName());
        
        return updated;
    }
    
    @Transactional
    @CacheEvict(value = "rules", allEntries = true)
    public void deleteRule(Long id, String deletedBy) {
        ruleRepository.deleteById(id);
        auditService.logRuleEvent(id, "DELETE", deletedBy, "Rule deleted");
    }
}
