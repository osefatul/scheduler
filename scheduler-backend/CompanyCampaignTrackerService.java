package com.usbank.corp.dcr.api.service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usbank.corp.dcr.api.entity.CampaignMapping;
import com.usbank.corp.dcr.api.entity.CompanyCampaignTracker;
import com.usbank.corp.dcr.api.repository.CompanyCampaignTrackerRepository;
import com.usbank.corp.dcr.api.utils.RotationUtils;

/**
 * Service for managing company-specific campaign usage tracking
 */
@Service
public class CompanyCampaignTrackerService {
    
    private static final Logger log = LoggerFactory.getLogger(CompanyCampaignTrackerService.class);
    
    private final CompanyCampaignTrackerRepository trackerRepository;
    private final RotationUtils rotationUtils;
    
    @Autowired
    public CompanyCampaignTrackerService(
            CompanyCampaignTrackerRepository trackerRepository, 
            RotationUtils rotationUtils) {
        this.trackerRepository = trackerRepository;
        this.rotationUtils = rotationUtils;
    }
    
    /**
     * Get or create tracker for a company-campaign pair
     * Initializes with campaign's frequency and capping values
     * 
     * @param companyId Company ID
     * @param campaign Campaign entity
     * @return The tracker entity
     */
    @Transactional
    public CompanyCampaignTracker getOrCreateTracker(String companyId, CampaignMapping campaign) {
        try {
            // Check explicitly if the tracker exists
            Optional<CompanyCampaignTracker> existingTracker = 
                    trackerRepository.findByCompanyIdAndCampaignId(companyId, campaign.getId());
            
            if (existingTracker.isPresent()) {
                CompanyCampaignTracker tracker = existingTracker.get();
                
                // Check if weekly frequency needs reset
                Date currentDate = new Date();
                Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
                
                if (tracker.getLastWeekReset() != null && 
                    tracker.getLastWeekReset().before(weekStartDate) && 
                    tracker.getOriginalWeeklyFrequency() != null) {
                    
                    log.info("Resetting weekly frequency for company {}, campaign {}", 
                            companyId, campaign.getId());
                    tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                    tracker.setLastWeekReset(weekStartDate);
                    tracker = trackerRepository.save(tracker);
                }
                
                return tracker;
            } else {
                // Before creating, double-check to avoid race conditions
                if (trackerRepository.existsByCompanyIdAndCampaignId(companyId, campaign.getId())) {
                    return trackerRepository.findByCompanyIdAndCampaignId(companyId, campaign.getId()).get();
                }
                
                // Create new tracker initialized with campaign values
                CompanyCampaignTracker tracker = new CompanyCampaignTracker();
                tracker.setCompanyId(companyId);
                tracker.setCampaignId(campaign.getId());
                
                // Initialize with campaign values
                tracker.setRemainingWeeklyFrequency(campaign.getFrequencyPerWeek());
                tracker.setOriginalWeeklyFrequency(campaign.getFrequencyPerWeek());
                tracker.setRemainingDisplayCap(campaign.getDisplayCapping());
                
                // Set initial dates
                Date currentDate = new Date();
                Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
                tracker.setLastUpdated(currentDate);
                tracker.setLastWeekReset(weekStartDate);
                
                log.info("Created new tracker for company {}, campaign {} with frequency {} and cap {}", 
                        companyId, campaign.getId(), 
                        tracker.getRemainingWeeklyFrequency(), 
                        tracker.getRemainingDisplayCap());
                
                try {
                    return trackerRepository.save(tracker);
                } catch (Exception e) {
                    // If insertion fails due to a duplicate key, try one more time to fetch
                    if (e.getMessage() != null && e.getMessage().contains("unique_company_campaign")) {
                        log.warn("Duplicate key when saving tracker - retrying fetch");
                        return trackerRepository.findByCompanyIdAndCampaignId(companyId, campaign.getId())
                                .orElseThrow(() -> e); // Rethrow if still can't find
                    }
                    throw e;
                }
            }
        } catch (Exception e) {
            log.error("Error in getOrCreateTracker for company {}, campaign {}: {}", 
                    companyId, campaign.getId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Apply a view to a company-campaign pair
     * Decrements frequency and capping counters
     * 
     * @param companyId Company ID
     * @param campaignId Campaign ID
     * @param currentDate Current date
     * @return true if successful, false if not eligible
     */
    @Transactional
    public boolean applyView(String companyId, String campaignId, Date currentDate) {
        Optional<CompanyCampaignTracker> trackerOpt = 
                trackerRepository.findByCompanyIdAndCampaignId(companyId, campaignId);
        
        if (!trackerOpt.isPresent()) {
            log.warn("No tracker found for company {}, campaign {}", companyId, campaignId);
            return false;
        }
        
        CompanyCampaignTracker tracker = trackerOpt.get();
        
        // Strict check - both must be > 0 to proceed
        if (tracker.getRemainingWeeklyFrequency() == null || tracker.getRemainingWeeklyFrequency() <= 0 ||
            tracker.getRemainingDisplayCap() == null || tracker.getRemainingDisplayCap() <= 0) {
            
            log.info("Campaign {} not eligible for company {}: freq={}, cap={}", 
                    campaignId, companyId, 
                    tracker.getRemainingWeeklyFrequency(),
                    tracker.getRemainingDisplayCap());
            return false;
        }
        
        // Decrement both counters
        tracker.setRemainingWeeklyFrequency(Math.max(0, tracker.getRemainingWeeklyFrequency() - 1));
        tracker.setRemainingDisplayCap(Math.max(0, tracker.getRemainingDisplayCap() - 1));
        tracker.setLastUpdated(currentDate);
        
        trackerRepository.save(tracker);
        
        log.info("Applied view for company {}, campaign {}. New freq: {}, new cap: {}", 
                companyId, campaignId, 
                tracker.getRemainingWeeklyFrequency(), 
                tracker.getRemainingDisplayCap());
        
        return true;
    }
    
    /**
     * Get all active trackers for a company
     * These have remaining weekly frequency and display cap
     * 
     * @param companyId Company ID
     * @return List of active trackers
     */
    public List<CompanyCampaignTracker> getActiveTrackersForCompany(String companyId) {
        return trackerRepository.findActiveTrackersForCompany(companyId);
    }
    
    /**
     * Weekly scheduled task to reset campaign frequencies for all trackers
     * Runs every Monday at 1:00 AM
     */
    @Scheduled(cron = "0 0 1 * * MON")
    @Transactional
    public void weeklyFrequencyReset() {
        log.info("Starting weekly tracker frequency reset");
        
        Date currentDate = new Date();
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        
        // Find all trackers needing reset
        List<CompanyCampaignTracker> trackersToReset = 
                trackerRepository.findTrackersNeedingFrequencyReset(weekStartDate);
        
        log.info("Found {} trackers needing frequency reset", trackersToReset.size());
        
        int resetCount = 0;
        for (CompanyCampaignTracker tracker : trackersToReset) {
            if (tracker.getOriginalWeeklyFrequency() != null) {
                tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                tracker.setLastWeekReset(weekStartDate);
                trackerRepository.save(tracker);
                resetCount++;
                
                log.debug("Reset frequency for company {}, campaign {} to {}", 
                        tracker.getCompanyId(), tracker.getCampaignId(), 
                        tracker.getRemainingWeeklyFrequency());
            }
        }
        
        log.info("Reset weekly frequency for {} trackers", resetCount);
    }
}