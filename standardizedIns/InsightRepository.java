package com.example.insightapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.insightapp.model.Insight;

public interface InsightRepository extends JpaRepository<Insight, Long> {
    
    // Add this method to the existing repository
    List<Insight> findByInsightTypeAndInsightSubType(String insightType, String insightSubType);
    
    // Query to find unique insight templates for a specific type and subtype
    @Query(value = """
        SELECT DISTINCT i.insight 
        FROM Insight i 
        WHERE i.insightType = :insightType 
        AND i.insightSubType = :insightSubType 
        ORDER BY i.insight
    """)
    List<String> findUniqueInsightsByTypeAndSubType(
            @Param("insightType") String insightType, 
            @Param("insightSubType") String insightSubType);
    
    // Query to find all top parent names (companies) for a specific insight
    @Query(value = """
        SELECT i.topParentName 
        FROM Insight i 
        WHERE i.insightType = :insightType 
        AND i.insightSubType = :insightSubType 
        AND i.insight = :insight
    """)
    List<String> findCompaniesForInsight(
            @Param("insightType") String insightType, 
            @Param("insightSubType") String insightSubType,
            @Param("insight") String insight);
}