package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.RuleRequest;
import com.example.stockbrokerage.entity.Rule;
import com.example.stockbrokerage.entity.Rule.RuleLevel;
import com.example.stockbrokerage.entity.Rule.RuleType;
import com.example.stockbrokerage.service.RuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/rules")
@RequiredArgsConstructor
@Tag(name = "Rule Management (Admin)", description = "Admin APIs for managing business rules")
public class RuleAdminController {
    
    private final RuleService ruleService;
    
    @PostMapping
    @Operation(summary = "Create a new rule", description = "Create a new business rule")
    public ResponseEntity<Rule> createRule(@Valid @RequestBody RuleRequest request) {
        Rule rule = ruleService.createRule(request, "ADMIN");
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get rule by ID", description = "Retrieve rule details by rule ID")
    public ResponseEntity<Rule> getRuleById(@PathVariable Long id) {
        Rule rule = ruleService.getRuleById(id);
        return ResponseEntity.ok(rule);
    }
    
    @GetMapping
    @Operation(summary = "Get all rules", description = "Retrieve all rules in the system")
    public ResponseEntity<List<Rule>> getAllRules() {
        List<Rule> rules = ruleService.getAllRules();
        return ResponseEntity.ok(rules);
    }
    
    @GetMapping("/active")
    @Operation(summary = "Get active rules", description = "Retrieve all active rules")
    public ResponseEntity<List<Rule>> getActiveRules() {
        List<Rule> rules = ruleService.getActiveRules();
        return ResponseEntity.ok(rules);
    }
    
    @GetMapping("/type/{ruleType}")
    @Operation(summary = "Get rules by type", description = "Retrieve rules by type")
    public ResponseEntity<List<Rule>> getRulesByType(@PathVariable RuleType ruleType) {
        List<Rule> rules = ruleService.getRulesByType(ruleType);
        return ResponseEntity.ok(rules);
    }
    
    @GetMapping("/level/{level}")
    @Operation(summary = "Get rules by level", description = "Retrieve rules by level (APPLICATION, CLIENT, TRADE)")
    public ResponseEntity<List<Rule>> getRulesByLevel(@PathVariable RuleLevel level) {
        List<Rule> rules = ruleService.getRulesByLevel(level);
        return ResponseEntity.ok(rules);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update rule", description = "Update an existing rule")
    public ResponseEntity<Rule> updateRule(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
        Rule rule = ruleService.updateRule(id, request, "ADMIN");
        return ResponseEntity.ok(rule);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete rule", description = "Remove a rule from the system")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        ruleService.deleteRule(id, "ADMIN");
        return ResponseEntity.noContent().build();
    }
}
