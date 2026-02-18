package com.example.stockbrokerage.dto;

import com.example.stockbrokerage.entity.Rule.RuleLevel;
import com.example.stockbrokerage.entity.Rule.RuleType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleRequest {
    
    @NotBlank(message = "Rule name is required")
    @Size(max = 100, message = "Rule name must be at most 100 characters")
    private String ruleName;
    
    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;
    
    @NotNull(message = "Rule type is required")
    private RuleType ruleType;
    
    @NotNull(message = "Rule level is required")
    private RuleLevel level;
    
    private Long clientId;
    
    @NotBlank(message = "Rule content is required")
    private String ruleContent;
    
    @NotNull(message = "Active status is required")
    private Boolean active;
    
    @NotNull(message = "Priority is required")
    @Min(value = 1, message = "Priority must be at least 1")
    private Integer priority;
}
