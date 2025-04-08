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
     * Check if any tracker for this company has been viewed this week
     * (has remaining frequency less than original frequency)
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM CompanyCampaignTracker t " +
           "WHERE t.companyId = :companyId " + 
           "AND t.lastWeekReset = :weekStartDate " +
           "AND t.remainingWeeklyFrequency < t.originalWeeklyFrequency")
    boolean hasCompanyViewedCampaignThisWeek(
            @Param("companyId") String companyId, 
            @Param("weekStartDate") Date weekStartDate);
    
    /**
     * Find the tracker that has been viewed this week for this company
     */
    @Query("SELECT t FROM CompanyCampaignTracker t " +
           "WHERE t.companyId = :companyId " + 
           "AND t.lastWeekReset = :weekStartDate " +
           "AND t.remainingWeeklyFrequency < t.originalWeeklyFrequency " +
           "AND t.remainingDisplayCap > 0")
    Optional<CompanyCampaignTracker> findViewedTrackerForCompanyThisWeek(
            @Param("companyId") String companyId, 
            @Param("weekStartDate") Date weekStartDate);
}