package com.usbank.corp.dcr.api.service;

import com.usbank.corp.dcr.api.entity.UserCampaignTracker;
import com.usbank.corp.dcr.api.repository.UserCampaignTrackerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Service to exhaust campaigns for specific users after conversion
 * Uses ONLY UserCampaignTracker exhaustion - no closure records
 */
@Slf4j
@Service
public class CampaignExhaustionService {

    private final UserCampaignTrackerRepository trackerRepository;

    public CampaignExhaustionService(UserCampaignTrackerRepository trackerRepository) {
        this.trackerRepository = trackerRepository;
    }

    /**
     * Exhaust a specific campaign for a specific user
     * This prevents them from seeing this campaign again while preserving access to other campaigns
     * 
     * @param userId User identifier
     * @param companyId Company identifier  
     * @param campaignId Campaign identifier to exhaust
     * @return true if exhaustion was successful
     */
    @Transactional
    public boolean exhaustCampaignForUser(String userId, String companyId, String campaignId) {
        log.info("=== CAMPAIGN EXHAUSTION START ===");
        log.info("Exhausting campaign {} for user {} in company {} (HOT LEAD CONVERSION)", 
                campaignId, userId, companyId);
        
        try {
            Date currentDate = new Date();
            Date currentWeekStart = getCurrentWeekStartDate(currentDate);
            
            // Step 1: Find and exhaust ALL existing trackers for this user-campaign combination
            List<UserCampaignTracker> existingTrackers = trackerRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
            
            log.info("Found {} existing tracker(s) for user {}, campaign {}", 
                    existingTrackers.size(), userId, campaignId);
            
            // Exhaust all existing trackers
            for (UserCampaignTracker tracker : existingTrackers) {
                log.info("Exhausting existing tracker: ID={}, Week={}, CurrentFreq={}, CurrentCap={}", 
                        tracker.getId(), tracker.getWeekStartDate(), 
                        tracker.getRemainingWeeklyFrequency(), tracker.getRemainingDisplayCap());
                
                // Set both counters to 0 (exhausted)
                tracker.setRemainingWeeklyFrequency(0);
                tracker.setRemainingDisplayCap(0);
                tracker.setLastViewDate(currentDate);
                
                UserCampaignTracker saved = trackerRepository.save(tracker);
                log.info("✅ Exhausted tracker: ID={}, NewFreq={}, NewCap={}", 
                        saved.getId(), saved.getRemainingWeeklyFrequency(), saved.getRemainingDisplayCap());
            }
            
            // Step 2: Create exhausted trackers for current and future weeks (to prevent new assignments)
            for (int weekOffset = 0; weekOffset <= 6; weekOffset++) { // Cover next 6 weeks
                Date weekStart = addWeeksToDate(currentWeekStart, weekOffset);
                
                // Check if tracker already exists for this week
                boolean trackerExists = existingTrackers.stream()
                    .anyMatch(t -> isSameWeek(t.getWeekStartDate(), weekStart));
                
                if (!trackerExists) {
                    log.info("Creating pre-exhausted tracker for week offset +{} ({})", weekOffset, weekStart);
                    
                    // Create new exhausted tracker
                    UserCampaignTracker newTracker = new UserCampaignTracker();
                    newTracker.setId(UUID.randomUUID().toString());
                    newTracker.setUserId(userId);
                    newTracker.setCompanyId(companyId);
                    newTracker.setCampaignId(campaignId);
                    newTracker.setWeekStartDate(weekStart);
                    
                    // Pre-exhaust: Set both counters to 0
                    newTracker.setRemainingWeeklyFrequency(0);
                    newTracker.setRemainingDisplayCap(0);
                    newTracker.setLastViewDate(currentDate);
                    
                    UserCampaignTracker saved = trackerRepository.save(newTracker);
                    
                    log.info("✅ Created pre-exhausted tracker: ID={}, Week={}, Freq=0, Cap=0", 
                            saved.getId(), saved.getWeekStartDate());
                }
            }
            
            log.info("=== CAMPAIGN EXHAUSTION COMPLETE ===");
            log.info("✅ Campaign {} successfully exhausted for user {}", campaignId, userId);
            log.info("User will NOT see campaign {} again but CAN see other campaigns normally", campaignId);
            
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to exhaust campaign {} for user {}: {}", campaignId, userId, e.getMessage(), e);
            log.info("=== CAMPAIGN EXHAUSTION FAILED ===");
            return false;
        }
    }
    
    /**
     * Get the start date of the current week (Monday at 00:00:00)
     */
    private Date getCurrentWeekStartDate(Date currentDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentDate);
        
        // Set to Monday of current week
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        
        return cal.getTime();
    }
    
    /**
     * Add weeks to a date
     */
    private Date addWeeksToDate(Date date, int weeks) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.WEEK_OF_YEAR, weeks);
        return cal.getTime();
    }
    
    /**
     * Check if two dates are in the same week
     */
    private boolean isSameWeek(Date date1, Date date2) {
        if (date1 == null || date2 == null) return false;
        
        // Normalize both dates to Monday 00:00:00 of their respective weeks
        Date week1Start = getCurrentWeekStartDate(date1);
        Date week2Start = getCurrentWeekStartDate(date2);
        
        return week1Start.equals(week2Start);
    }
    
    /**
     * Verify that a campaign is properly exhausted for a user
     * Useful for testing and debugging
     */
    public boolean verifyCampaignExhaustion(String userId, String companyId, String campaignId) {
        log.info("Verifying campaign exhaustion for user {}, campaign {}", userId, campaignId);
        
        List<UserCampaignTracker> trackers = trackerRepository
            .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
        
        if (trackers.isEmpty()) {
            log.warn("No trackers found for user {}, campaign {} - exhaustion verification inconclusive", 
                    userId, campaignId);
            return false;
        }
        
        boolean allExhausted = true;
        for (UserCampaignTracker tracker : trackers) {
            boolean isExhausted = (tracker.getRemainingDisplayCap() != null && tracker.getRemainingDisplayCap() <= 0) ||
                                 (tracker.getRemainingWeeklyFrequency() != null && tracker.getRemainingWeeklyFrequency() <= 0);
            
            log.info("Tracker for week {}: Freq={}, Cap={}, Exhausted={}", 
                    tracker.getWeekStartDate(), tracker.getRemainingWeeklyFrequency(), 
                    tracker.getRemainingDisplayCap(), isExhausted);
            
            if (!isExhausted) {
                allExhausted = false;
            }
        }
        
        log.info("Campaign {} exhaustion verification for user {}: {}", campaignId, userId, 
                allExhausted ? "✅ PROPERLY EXHAUSTED" : "❌ NOT FULLY EXHAUSTED");
        
        return allExhausted;
    }
}