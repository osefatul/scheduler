package com.usbank.corp.dcr.api.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.usbank.corp.dcr.api.entity.CompanyCampaignTracker;

/**
 * Repository for company-specific campaign usage tracking
 */
@Repository
public interface CompanyCampaignTrackerRepository extends JpaRepository<CompanyCampaignTracker, String> {
    
    /**
     * Find all trackers for a company
     */
    List<CompanyCampaignTracker> findByCompanyId(String companyId);
    
    /**
     * Find all trackers for a campaign
     */
    List<CompanyCampaignTracker> findByCampaignId(String campaignId);
    
    /**
     * Find tracker for specific company and campaign
     */
    Optional<CompanyCampaignTracker> findByCompanyIdAndCampaignId(String companyId, String campaignId);
    
    /**
     * Check if tracker exists for company and campaign
     */
    boolean existsByCompanyIdAndCampaignId(String companyId, String campaignId);
    
    /**
     * Find all trackers that need weekly frequency reset
     */
    @Query("SELECT t FROM CompanyCampaignTracker t WHERE t.lastWeekReset < :weekStartDate")
    List<CompanyCampaignTracker> findTrackersNeedingFrequencyReset(@Param("weekStartDate") Date weekStartDate);
    
    /**
     * Find all active trackers for a company (with remaining frequency and display cap)
     */
    @Query("SELECT t FROM CompanyCampaignTracker t " +
           "WHERE t.companyId = :companyId " +
           "AND t.remainingWeeklyFrequency > 0 " +
           "AND t.remainingDisplayCap > 0")
    List<CompanyCampaignTracker> findActiveTrackersForCompany(@Param("companyId") String companyId);
    
    /**
     * Check if any campaign has been viewed by this company this week
     * This is crucial for the "one campaign per week" rule
     * It checks if ANY campaign for this company has been viewed this week
     * (has lastUpdated in this week and lastWeekReset matching current week)
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM CompanyCampaignTracker t " +
           "WHERE t.companyId = :companyId " + 
           "AND t.lastWeekReset = :weekStartDate " +
           "AND (t.originalWeeklyFrequency > t.remainingWeeklyFrequency OR t.remainingWeeklyFrequency = 0)")
    boolean hasCompanyViewedCampaignThisWeek(
           @Param("companyId") String companyId, 
           @Param("weekStartDate") Date weekStartDate);
    
    /**
     * Find any tracker that has been viewed this week for this company
     * We need to check for ANY campaign that was viewed, even if its frequency is now 0
     */
    @Query("SELECT t FROM CompanyCampaignTracker t " +
           "WHERE t.companyId = :companyId " + 
           "AND t.lastWeekReset = :weekStartDate " +
           "AND (t.originalWeeklyFrequency > t.remainingWeeklyFrequency OR t.remainingWeeklyFrequency = 0) " +
           "ORDER BY t.lastUpdated DESC")
    List<CompanyCampaignTracker> findViewedTrackersForCompanyThisWeek(
           @Param("companyId") String companyId, 
           @Param("weekStartDate") Date weekStartDate);
}