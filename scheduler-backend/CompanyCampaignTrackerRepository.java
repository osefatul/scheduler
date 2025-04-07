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
     * Find tracker for specific company and campaign
     */
    Optional<CompanyCampaignTracker> findByCompanyIdAndCampaignId(String companyId, String campaignId);
    
    /**
     * Find all trackers for a company
     */
    List<CompanyCampaignTracker> findByCompanyId(String companyId);
    
    /**
     * Find all trackers for a campaign
     */
    List<CompanyCampaignTracker> findByCampaignId(String campaignId);
    
    /**
     * Find all trackers that need weekly frequency reset
     * (last reset date before provided week start date)
     */
    @Query("SELECT t FROM CompanyCampaignTracker t WHERE t.lastWeekReset < :weekStartDate")
    List<CompanyCampaignTracker> findTrackersNeedingFrequencyReset(@Param("weekStartDate") Date weekStartDate);
    
    /**
     * Find all eligible trackers for a company that have remaining frequency and display cap
     */
    @Query("SELECT t FROM CompanyCampaignTracker t " +
           "WHERE t.companyId = :companyId " +
           "AND t.remainingWeeklyFrequency > 0 " +
           "AND t.remainingDisplayCap > 0")
    List<CompanyCampaignTracker> findEligibleTrackersForCompany(@Param("companyId") String companyId);
}