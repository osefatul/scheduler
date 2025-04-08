package com.example.insightapp.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.insightapp.model.Insight;
import com.example.insightapp.model.StandardizedInsight;
import com.example.insightapp.repository.InsightRepository;

@Service
public class StandardizedInsightService {

    private final InsightRepository insightRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public StandardizedInsightService(InsightRepository insightRepository, JdbcTemplate jdbcTemplate) {
        this.insightRepository = insightRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StandardizedInsight> getStandardizedInsights(String insightType, String insightSubType) {
        // Fetch insights matching the type and subtype
        List<Insight> insights = insightRepository.findByInsightTypeAndInsightSubType(insightType, insightSubType);
        
        // Map to store standardized insights
        Map<String, StandardizedInsight> standardizedInsightsMap = new HashMap<>();
        
        // Process each insight to standardize it
        for (Insight insight : insights) {
            String originalText = insight.getInsight();
            String standardizedText = standardizeInsightText(originalText);
            
            // If we already have this standardized text, update the existing entry
            if (standardizedInsightsMap.containsKey(standardizedText)) {
                StandardizedInsight existingEntry = standardizedInsightsMap.get(standardizedText);
                existingEntry.getCompanies().add(insight.getTopParentName());
                existingEntry.setCompanyCount(existingEntry.getCompanyCount() + 1);
            } else {
                // Create a new entry for this standardized text
                List<String> companies = new ArrayList<>();
                companies.add(insight.getTopParentName());
                StandardizedInsight newEntry = new StandardizedInsight(standardizedText, 1, companies);
                standardizedInsightsMap.put(standardizedText, newEntry);
            }
        }
        
        // Convert the map to a list for returning
        return new ArrayList<>(standardizedInsightsMap.values());
    }
    
    public List<StandardizedInsight> getStandardizedInsightsNative(String insightType, String insightSubType) {
        // SQL query with debugging
        try {
            // Use a simpler SQL query that's compatible with older SQL Server versions
            String sql = """
                SELECT 
                    CASE
                        WHEN [Insight] LIKE 'While %' AND [Insight] LIKE '% already uses %' AND [Insight] LIKE '%could have used%' THEN
                            'While [Company] already uses [Service] amounting to [Amount] in [Countries] countries, [Company] could have used USB FX services'
                        WHEN [Insight] LIKE '%could have used USB FX services%' THEN
                            '[Company] could have used USB FX services amounting to [Amount] in [Countries] countries'
                        WHEN [Insight] LIKE '%has FX needs based on payment patterns%' THEN
                            '[Company] has FX needs based on payment patterns'
                        ELSE [Insight]
                    END AS standardized_text,
                    COUNT(DISTINCT [Top_Parent_Name]) AS company_count
                FROM vw_export_test
                WHERE [Insight_Type] = ? AND [Insight_Sub_Type] = ?
                GROUP BY 
                    CASE
                        WHEN [Insight] LIKE 'While %' AND [Insight] LIKE '% already uses %' AND [Insight] LIKE '%could have used%' THEN
                            'While [Company] already uses [Service] amounting to [Amount] in [Countries] countries, [Company] could have used USB FX services'
                        WHEN [Insight] LIKE '%could have used USB FX services%' THEN
                            '[Company] could have used USB FX services amounting to [Amount] in [Countries] countries'
                        WHEN [Insight] LIKE '%has FX needs based on payment patterns%' THEN
                            '[Company] has FX needs based on payment patterns'
                        ELSE [Insight]
                    END
                ORDER BY company_count DESC
            """;
            
            // First get the standardized insights with counts
            List<Map<String, Object>> standardizedResults = jdbcTemplate.queryForList(sql, insightType, insightSubType);
            
            // Now for each standardized insight, get the companies
            List<StandardizedInsight> result = new ArrayList<>();
            
            for (Map<String, Object> row : standardizedResults) {
                String standardizedText = (String) row.get("standardized_text");
                int companyCount = ((Number) row.get("company_count")).intValue();
                
                // Get companies for this standardized insight with a separate query
                String companiesSql = """
                    SELECT DISTINCT [Top_Parent_Name]
                    FROM vw_export_test
                    WHERE [Insight_Type] = ? 
                    AND [Insight_Sub_Type] = ?
                    AND CASE
                        WHEN [Insight] LIKE 'While %' AND [Insight] LIKE '% already uses %' AND [Insight] LIKE '%could have used%' THEN
                            'While [Company] already uses [Service] amounting to [Amount] in [Countries] countries, [Company] could have used USB FX services'
                        WHEN [Insight] LIKE '%could have used USB FX services%' THEN
                            '[Company] could have used USB FX services amounting to [Amount] in [Countries] countries'
                        WHEN [Insight] LIKE '%has FX needs based on payment patterns%' THEN
                            '[Company] has FX needs based on payment patterns'
                        ELSE [Insight]
                    END = ?
                """;
                
                List<String> companies = jdbcTemplate.queryForList(companiesSql, String.class, 
                        insightType, insightSubType, standardizedText);
                
                result.add(new StandardizedInsight(standardizedText, companyCount, companies));
            }
            
            return result;
        } catch (Exception e) {
            // Log the error and return an empty list or throw a custom exception
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    /**
     * Standardizes insight text by replacing specific variables with placeholders.
     */
    private String standardizeInsightText(String originalText) {
        String standardizedText = originalText;
        
        // Pattern matching for "While X already uses Y..." format
        if (originalText.startsWith("While") && originalText.contains("already uses") && originalText.contains("could have used")) {
            // Replace company name in first part
            Pattern companyPattern1 = Pattern.compile("While (.*?) already uses");
            Matcher companyMatcher1 = companyPattern1.matcher(originalText);
            if (companyMatcher1.find()) {
                standardizedText = standardizedText.replace(companyMatcher1.group(1), "[Company]");
            }
            
            // Replace service
            Pattern servicePattern = Pattern.compile("already uses (.*?) amounting");
            Matcher serviceMatcher = servicePattern.matcher(originalText);
            if (serviceMatcher.find()) {
                standardizedText = standardizedText.replace(serviceMatcher.group(1), "[Service]");
            }
            
            // Replace amount
            Pattern amountPattern = Pattern.compile("amounting to (.*?) in");
            Matcher amountMatcher = amountPattern.matcher(originalText);
            if (amountMatcher.find()) {
                standardizedText = standardizedText.replace(amountMatcher.group(1), "[Amount]");
            }
            
            // Replace countries
            Pattern countriesPattern = Pattern.compile("in (.*?) countries");
            Matcher countriesMatcher = countriesPattern.matcher(originalText);
            if (countriesMatcher.find()) {
                standardizedText = standardizedText.replace(countriesMatcher.group(1), "[Countries]");
            }
            
            // Replace company name in second part
            int commaIndex = originalText.indexOf(',');
            if (commaIndex > 0) {
                String secondPart = originalText.substring(commaIndex + 1).trim();
                Pattern companyPattern2 = Pattern.compile("^(.*?) could have used");
                Matcher companyMatcher2 = companyPattern2.matcher(secondPart);
                if (companyMatcher2.find()) {
                    standardizedText = standardizedText.replace(companyMatcher2.group(1), "[Company]");
                }
            }
        } 
        // Pattern matching for "X could have used USB FX services" format
        else if (originalText.contains("could have used USB FX services")) {
            // Replace company name
            Pattern companyPattern = Pattern.compile("^(.*?) could have used");
            Matcher companyMatcher = companyPattern.matcher(originalText);
            if (companyMatcher.find()) {
                standardizedText = standardizedText.replace(companyMatcher.group(1), "[Company]");
            }
            
            // Replace amount if present
            if (originalText.contains("amounting to")) {
                Pattern amountPattern = Pattern.compile("amounting to (.*?) in");
                Matcher amountMatcher = amountPattern.matcher(originalText);
                if (amountMatcher.find()) {
                    standardizedText = standardizedText.replace(amountMatcher.group(1), "[Amount]");
                }
            }
            
            // Replace countries if present
            if (originalText.contains("countries")) {
                Pattern countriesPattern = Pattern.compile("in (.*?) countries");
                Matcher countriesMatcher = countriesPattern.matcher(originalText);
                if (countriesMatcher.find()) {
                    standardizedText = standardizedText.replace(countriesMatcher.group(1), "[Countries]");
                }
            }
        }
        // Pattern for "has FX needs based on payment patterns"
        else if (originalText.contains("has FX needs based on payment patterns")) {
            Pattern companyPattern = Pattern.compile("^(.*?) has FX needs");
            Matcher companyMatcher = companyPattern.matcher(originalText);
            if (companyMatcher.find()) {
                standardizedText = standardizedText.replace(companyMatcher.group(1), "[Company]");
            }
        }
        
        return standardizedText;
    }
}