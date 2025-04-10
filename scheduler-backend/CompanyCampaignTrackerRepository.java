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
     * Check if any tracker has been updated this week for this company
     * This is crucial for the "one campaign per week" rule
     * It checks if ANY tracker was updated in this week - even if its frequency is now 0
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM CompanyCampaignTracker t " +
           "WHERE t.companyId = :companyId " + 
           "AND t.lastWeekReset = :weekStartDate " +
           "AND t.lastUpdated >= t.lastWeekReset")
    boolean hasCompanyViewedAnyCampaignThisWeek(
           @Param("companyId") String companyId, 
           @Param("weekStartDate") Date weekStartDate);
    
    /**
     * Find any trackers updated this week for this company, regardless of remaining frequency
     * Will return trackers even if their frequency is now 0
     */
    @Query("SELECT t FROM CompanyCampaignTracker t " +
           "WHERE t.companyId = :companyId " + 
           "AND t.lastWeekReset = :weekStartDate " +
           "AND t.lastUpdated >= t.lastWeekReset " +
           "ORDER BY t.lastUpdated DESC")
    List<CompanyCampaignTracker> findTrackersUpdatedThisWeek(
           @Param("companyId") String companyId, 
           @Param("weekStartDate") Date weekStartDate);


           @Query("SELECT t FROM CompanyCampaignTracker t " +
           "WHERE t.companyId = :companyId " +
           "ORDER BY t.lastUpdated DESC")
    List<CompanyCampaignTracker> findTrackersByCompanyOrderByLastUpdated(
            @Param("companyId") String companyId);
}