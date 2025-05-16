package com.usbank.corp.dcr.api.service;

import java.text.SimpleDateFormat;
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
     * Check if a company has viewed any campaign this week, regardless of remaining frequency
     * 
     * @param companyId Company ID
     * @param currentDate Current date
     * @return true if the company has viewed a campaign this week
     */
    public boolean hasCompanyViewedAnyCampaignThisWeek(String companyId, Date currentDate) {
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        return trackerRepository.hasCompanyViewedAnyCampaignThisWeek(companyId, weekStartDate);
    }
    
    /**
     * Get the tracker for the campaign viewed by this company this week
     * Will return trackers even if their frequency is now 0
     * 
     * @param companyId Company ID
     * @param currentDate Current date
     * @return Optional containing the tracker if found
     */
    public Optional<CompanyCampaignTracker> getViewedTrackerForCompanyThisWeek(String companyId, Date currentDate) {
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        List<CompanyCampaignTracker> viewedTrackers = 
            trackerRepository.findTrackersUpdatedThisWeek(companyId, weekStartDate);
        
        if (viewedTrackers.isEmpty()) {
            return Optional.empty();
        }
        
        // Return the most recently viewed tracker
        return Optional.of(viewedTrackers.get(0));
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
            
            // CRITICAL FIX: Only reset if we're in a new week
            Date currentDate = new Date();
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            log.info("Tracker dates: lastWeekReset={}, currentWeekStart={}", 
                     tracker.getLastWeekReset(), weekStartDate);
            
            // ONLY reset if the lastWeekReset is BEFORE the current week start
            if (tracker.getLastWeekReset() != null && 
                tracker.getLastWeekReset().before(weekStartDate) && 
                tracker.getOriginalWeeklyFrequency() != null) {
                
                log.info("RESETTING WEEKLY FREQUENCY for company {}, campaign {}", 
                        companyId, campaign.getId());
                tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                tracker.setLastWeekReset(weekStartDate);
                tracker = trackerRepository.save(tracker);
            } else {
                log.info("NOT RESETTING frequency for company {}, campaign {} - already in current week", 
                        companyId, campaign.getId());
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
        
        log.info("BEFORE VIEW: Company={}, Campaign={}, Freq={}/{}, Cap={}", 
                 companyId, campaignId, 
                 tracker.getRemainingWeeklyFrequency(), 
                 tracker.getOriginalWeeklyFrequency(),
                 tracker.getRemainingDisplayCap());
        
        // Strict check - both must be > 0 to proceed
        if (tracker.getRemainingWeeklyFrequency() == null || tracker.getRemainingWeeklyFrequency() <= 0) {
            log.info("Cannot apply view - FREQUENCY EXHAUSTED: Company={}, Campaign={}, Freq={}", 
                    companyId, campaignId, tracker.getRemainingWeeklyFrequency());
            return false;
        }
        
        if (tracker.getRemainingDisplayCap() == null || tracker.getRemainingDisplayCap() <= 0) {
            log.info("Cannot apply view - DISPLAY CAP EXHAUSTED: Company={}, Campaign={}, Cap={}", 
                    companyId, campaignId, tracker.getRemainingDisplayCap());
            return false;
        }
        
        // Decrement both counters
        tracker.setRemainingWeeklyFrequency(Math.max(0, tracker.getRemainingWeeklyFrequency() - 1));
        tracker.setRemainingDisplayCap(Math.max(0, tracker.getRemainingDisplayCap() - 1));
        tracker.setLastUpdated(currentDate);
        
        // Add explicit debug of the current week
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        log.info("Current week start: {}, Tracker week reset: {}", 
                 sdf.format(weekStartDate),
                 tracker.getLastWeekReset() != null ? sdf.format(tracker.getLastWeekReset()) : "null");
        
        trackerRepository.save(tracker);
        
        log.info("AFTER VIEW: Company={}, Campaign={}, NEW Freq={}/{}, NEW Cap={}", 
                 companyId, campaignId, 
                 tracker.getRemainingWeeklyFrequency(), 
                 tracker.getOriginalWeeklyFrequency(),
                 tracker.getRemainingDisplayCap());
        
        return true;
    }
    
    /**
     * Reset frequencies for all trackers for a company
     * This can be called manually if weekly resets aren't working correctly
     * 
     * @param companyId Company ID to reset trackers for
     * @return Number of trackers reset
     */
    @Transactional
    public int resetFrequenciesForCompany(String companyId) {
        log.info("Manually resetting frequencies for company {}", companyId);
        
        Date currentDate = new Date();
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        
        List<CompanyCampaignTracker> trackers = trackerRepository.findByCompanyId(companyId);
        int resetCount = 0;
        
        for (CompanyCampaignTracker tracker : trackers) {
            if (tracker.getOriginalWeeklyFrequency() != null) {
                // Always reset to original frequency regardless of last reset time
                tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                tracker.setLastWeekReset(weekStartDate);
                trackerRepository.save(tracker);
                resetCount++;
                
                log.info("Reset frequency for company {}, campaign {} to {}", 
                        tracker.getCompanyId(), tracker.getCampaignId(), 
                        tracker.getRemainingWeeklyFrequency());
            }
        }
        
        return resetCount;
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