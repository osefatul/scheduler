package com.example.insightapp.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.insightapp.model.StandardizedInsight;
import com.example.insightapp.service.StandardizedInsightService;

@RestController
@RequestMapping("/api/insights")
@CrossOrigin(origins = "*") // For development - restrict in production
public class StandardizedInsightController {
    
    private final StandardizedInsightService standardizedInsightService;
    
    @Autowired
    public StandardizedInsightController(StandardizedInsightService standardizedInsightService) {
        this.standardizedInsightService = standardizedInsightService;
    }
    
    /**
     * Get standardized insights grouped by template with company counts
     * 
     * @param insightType The insight type to filter by
     * @param insightSubType The insight subtype to filter by
     * @return List of standardized insights with company mappings
     */
    @GetMapping("/standardized")
    public ResponseEntity<List<StandardizedInsight>> getStandardizedInsights(
            @RequestParam("insightType") String insightType,
            @RequestParam("insightSubType") String insightSubType) {
        
        List<StandardizedInsight> standardizedInsights = 
            standardizedInsightService.getStandardizedInsightsNative(insightType, insightSubType);
        
        return ResponseEntity.ok(standardizedInsights);
    }
}