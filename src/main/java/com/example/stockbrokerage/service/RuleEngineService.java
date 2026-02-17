package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.Rule;
import com.example.stockbrokerage.entity.Rule.RuleLevel;
import com.example.stockbrokerage.entity.Trade;
import com.example.stockbrokerage.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngineService {
    
    private final RuleRepository ruleRepository;
    private final KieServices kieServices = KieServices.Factory.get();
    
    public Map<String, Object> evaluateTrade(Trade trade, Long clientId) {
        Map<String, Object> result = new HashMap<>();
        result.put("approved", true);
        result.put("reasons", new StringBuilder());
        
        try {
            // Load active rules
            List<Rule> applicationRules = ruleRepository.findByLevelAndActiveTrue(RuleLevel.APPLICATION);
            List<Rule> clientRules = ruleRepository.findByClientIdAndActiveTrue(clientId);
            List<Rule> tradeRules = ruleRepository.findByLevelAndActiveTrue(RuleLevel.TRADE);
            
            // If no rules exist, skip rule evaluation and approve
            if (applicationRules.isEmpty() && clientRules.isEmpty() && tradeRules.isEmpty()) {
                log.debug("No active rules found, trade approved by default");
                return result;
            }
            
            // Create KIE container with rules
            KieFileSystem kfs = kieServices.newKieFileSystem();
            
            int ruleIndex = 0;
            for (Rule rule : applicationRules) {
                String resourcePath = "src/main/resources/rules/app_rule_" + ruleIndex++ + ".drl";
                kfs.write(resourcePath, rule.getRuleContent());
            }
            
            for (Rule rule : clientRules) {
                String resourcePath = "src/main/resources/rules/client_rule_" + ruleIndex++ + ".drl";
                kfs.write(resourcePath, rule.getRuleContent());
            }
            
            for (Rule rule : tradeRules) {
                String resourcePath = "src/main/resources/rules/trade_rule_" + ruleIndex++ + ".drl";
                kfs.write(resourcePath, rule.getRuleContent());
            }
            
            KieBuilder kb = kieServices.newKieBuilder(kfs);
            kb.buildAll();
            
            if (kb.getResults().hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
                log.error("Rule compilation errors: {}", kb.getResults().getMessages());
                result.put("approved", false);
                result.put("reasons", "Rule compilation error");
                return result;
            }
            
            KieRepository kieRepository = kieServices.getRepository();
            KieContainer kContainer = kieServices.newKieContainer(kieRepository.getDefaultReleaseId());
            KieSession kSession = kContainer.newKieSession();
            
            // Insert trade into session
            kSession.insert(trade);
            kSession.setGlobal("result", result);
            
            // Fire all rules
            kSession.fireAllRules();
            kSession.dispose();
            
        } catch (Exception e) {
            log.error("Error evaluating rules", e);
            result.put("approved", false);
            result.put("reasons", "Rule evaluation error: " + e.getMessage());
        }
        
        return result;
    }
}
