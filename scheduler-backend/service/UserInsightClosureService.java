package com.usbank.corp.dcr.api.service;

import com.usbank.corp.dcr.api.entity.*;
import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.model.*;
import com.usbank.corp.dcr.api.repository.*;

import entity.UserGlobalPreference;
import entity.UserInsightClosure;
import lombok.extern.slf4j.Slf4j;
import model.CampaignWaitStatusDTO;
import model.ClosureStatisticsDTO;
import model.InsightClosureResponseDTO;
import repository.UserCampaignTrackerRepository;
import repository.UserGlobalPreferenceRepository;
import repository.UserInsightClosureRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserInsightClosureService {
    
    @Autowired
    private UserInsightClosureRepository closureRepository;
    
    @Autowired
    private UserGlobalPreferenceRepository globalPreferenceRepository;
    
    @Autowired
    private UserCampaignTrackerRepository userTrackerRepository;
    
    /**
     * Record an insight closure by a user (backward compatible)
     */
    @Transactional
    public InsightClosureResponseDTO recordInsightClosure(String userId, String companyId, 
            String campaignId) throws DataHandlingException {
        return recordInsightClosure(userId, companyId, campaignId, new Date());
    }
    
    /**
     * Record an insight closure by a user with specific date
     * FIXED: Proper logic for weekly rotation system with waiting periods
     */
    @Transactional
public InsightClosureResponseDTO recordInsightClosure(String userId, String companyId, 
        String campaignId, Date effectiveDate) throws DataHandlingException {

    log.info("Recording insight closure for user: {}, company: {}, campaign: {} at date: {}", 
            userId, companyId, campaignId, effectiveDate);

    // Check if user has global opt-out
    if (isUserGloballyOptedOut(userId, effectiveDate)) {
        throw new DataHandlingException(HttpStatus.FORBIDDEN.toString(), 
                "User has opted out of all insights");
    }

    // Check if user was previously in wait period and is now out
    boolean wasInWaitPeriod = wasUserPreviouslyInWaitPeriod(userId, companyId, effectiveDate);
    boolean isCurrentlyInWaitPeriod = isUserInWaitPeriod(userId, companyId, effectiveDate);
    
    log.info("Wait period status - Previously: {}, Currently: {}", wasInWaitPeriod, isCurrentlyInWaitPeriod);

    InsightClosureResponseDTO response = new InsightClosureResponseDTO();
    response.setCampaignId(campaignId);
    response.setEffectiveDate(effectiveDate);

    if (wasInWaitPeriod && !isCurrentlyInWaitPeriod) {
        // User was in wait period but now it's expired - use VIRTUAL closure counting
        int virtualClosureCount = getVirtualClosureCountForPostWaitPeriod(userId, companyId, campaignId, effectiveDate);
        
        log.info("POST-WAIT-PERIOD: Virtual closure count for campaign {}: {}", campaignId, virtualClosureCount);
        
        if (virtualClosureCount == 0) {
            // First virtual closure - HIDE banner
            log.info("POST-WAIT-PERIOD: First virtual closure for campaign {} - hiding banner", campaignId);
            
            incrementVirtualClosureCount(userId, companyId, campaignId, effectiveDate);
            
            response.setClosureCount(1);
            response.setAction("RECORDED_FIRST_CLOSURE");
            response.setMessage("Closure recorded. Banner hidden for this session.");
            response.setRequiresUserInput(false);
            
        } else {
            // Second+ virtual closure - show SecondClosurePopup
            log.info("POST-WAIT-PERIOD: Second+ virtual closure ({}) for campaign {} - showing SecondClosurePopup", 
                    virtualClosureCount + 1, campaignId);
            
            incrementVirtualClosureCount(userId, companyId, campaignId, effectiveDate);
            
            // Also increment the actual closure count for database consistency
            UserInsightClosure closure = closureRepository
                    .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId)
                    .orElse(createNewClosure(userId, companyId, campaignId));
            closure.setClosureCount(closure.getClosureCount() + 1);
            closure.setLastClosureDate(effectiveDate);
            closure.setUpdatedDate(effectiveDate);
            closureRepository.save(closure);
            
            response.setClosureCount(virtualClosureCount + 1);
            response.setAction("PROMPT_GLOBAL_PREFERENCE");
            response.setMessage("We've noticed you're not interested in other products. Would you like to stop seeing them?");
            response.setRequiresUserInput(true);
            response.setIsGlobalPrompt(true);
        }
        
    } else {
        // Normal logic for users who haven't been in wait period OR are currently in wait period
        
        // Find or create closure record
        UserInsightClosure closure = closureRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId)
                .orElse(createNewClosure(userId, companyId, campaignId));

        // Increment closure count
        closure.setClosureCount(closure.getClosureCount() + 1);
        closure.setLastClosureDate(effectiveDate);
        response.setClosureCount(closure.getClosureCount());

        if (closure.getClosureCount() == 1) {
            // FIRST CLOSURE: Hide banner
            log.info("NORMAL: First closure for user {}. Hiding banner (closure count: {})", 
                    userId, closure.getClosureCount());
            closure.setFirstClosureDate(effectiveDate);
            response.setAction("RECORDED_FIRST_CLOSURE");
            response.setMessage("Closure recorded. Banner hidden for this session.");
            response.setRequiresUserInput(false);
            
        } else {
            // SECOND+ CLOSURE: Show appropriate popup
            if (wasInWaitPeriod) {
                // User was in wait period (but still is) - show SecondClosurePopup
                log.info("NORMAL: Second+ closure ({}) for user {} who was in wait period. Showing SecondClosurePopup", 
                        closure.getClosureCount(), userId);
                response.setAction("PROMPT_GLOBAL_PREFERENCE");
                response.setMessage("We've noticed you're not interested in other products. Would you like to stop seeing them?");
                response.setRequiresUserInput(true);
                response.setIsGlobalPrompt(true);
            } else {
                // User has never been in wait period - show FirstClosurePopup
                log.info("NORMAL: Second+ closure ({}) for user {} who has never been in wait period. Showing FirstClosurePopup", 
                        closure.getClosureCount(), userId);
                response.setAction("PROMPT_CAMPAIGN_PREFERENCE");
                response.setMessage("Would you like to see this campaign again in the future?");
                response.setRequiresUserInput(true);
                response.setIsGlobalPrompt(false);
            }
        }

        closure.setUpdatedDate(effectiveDate);
        closureRepository.save(closure);
    }

    return response;
}
    
    
    /**
     * Handle user's response to preference prompt (backward compatible)
     */
    @Transactional
    public void handlePreferenceResponse(String userId, String companyId, String campaignId,
            boolean wantsToSee, String reason, boolean isGlobalResponse) throws DataHandlingException {
        handlePreferenceResponse(userId, companyId, campaignId, wantsToSee, reason, isGlobalResponse, new Date());
    }
    
    /**
     * Handle user's response to preference prompt
     * FIXED: Proper handling of waiting periods and permanent blocks
     */
    @Transactional
    public void handlePreferenceResponse(String userId, String companyId, String campaignId,
            boolean wantsToSee, String reason, boolean isGlobalResponse, Date effectiveDate) 
            throws DataHandlingException {

        log.info("Handling preference response for user: {}, campaign: {}, wantsToSee: {}, isGlobal: {} at date: {}", 
                userId, campaignId, wantsToSee, isGlobalResponse, effectiveDate);

        if (isGlobalResponse) {
            // This is response from SecondClosurePopup
            if (wantsToSee) {
                // User chose "Close now but show in future" - 1-month wait for ALL campaigns
                log.info("User chose 'show in future'. Setting 1-month wait for ALL campaigns");
                setOneMonthWaitForAllCampaigns(userId, companyId, reason, effectiveDate);
            } else {
                // User chose "Stop showing this ad" - GLOBAL OPT-OUT
                log.info("User chose 'stop showing ads'. Global opt-out for user {}", userId);
                handleGlobalOptOut(userId, reason, effectiveDate);
            }
        } else {
            // This is response from FirstClosurePopup
            UserInsightClosure closure = closureRepository
                    .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId)
                    .orElseThrow(() -> new DataHandlingException(HttpStatus.NOT_FOUND.toString(),
                            "No closure record found"));
            
            log.info("=== FIRST CLOSURE PREFERENCE RESPONSE ===");
            log.info("BEFORE: closureCount={}, permanent={}, nextEligible={}, reason={}", 
                     closure.getClosureCount(), closure.getPermanentlyClosed(), 
                     closure.getNextEligibleDate(), closure.getClosureReason());
            
            if (wantsToSee) {
                // User chose "Close & show later" - temporary close for this session/week only
                log.info("User chose 'close & show later' for campaign {}. Temporary close only.", campaignId);
                
                // Keep closure count, clear any blocking flags, set reason
                closure.setPermanentlyClosed(false);
                closure.setNextEligibleDate(null);
                closure.setClosureReason(reason != null ? reason : "User chose to see campaign later");
                
                // NO waiting period - campaign can appear again in future weeks
                
            } else {
                // User chose "Don't show again" - PERMANENT block + 1-month wait for ALL campaigns
                log.info("User chose 'don't show again' for campaign {}. Permanent block + 1-month wait for ALL campaigns.", campaignId);
                
                // 1. Permanently block THIS campaign
                closure.setClosureReason(reason != null ? reason : "User chose not to see campaign again");
                closure.setPermanentlyClosed(true);
                
                // Set nextEligibleDate far in future for this campaign (permanent block)
                Calendar cal = Calendar.getInstance();
                cal.setTime(effectiveDate);
                cal.add(Calendar.YEAR, 10);  // 10 years = effectively permanent for this campaign
                closure.setNextEligibleDate(cal.getTime());
                
                log.info("Campaign {} permanently blocked with nextEligibleDate: {}", 
                         campaignId, closure.getNextEligibleDate());
                
                // 2. Set 1-month waiting period for ALL campaigns
                setOneMonthWaitForAllCampaigns(userId, companyId, 
                    "User chose 'don't show again' for campaign " + campaignId, effectiveDate);
            }
            
            closure.setUpdatedDate(effectiveDate);
            UserInsightClosure saved = closureRepository.save(closure);
            
            log.info("AFTER SAVE: closureCount={}, permanent={}, nextEligible={}, reason={}", 
                     saved.getClosureCount(), saved.getPermanentlyClosed(), 
                     saved.getNextEligibleDate(), saved.getClosureReason());
            log.info("=== FIRST CLOSURE PREFERENCE COMPLETE ===");
        }
    }
    
    /**
     * Set 1-month wait for ALL campaigns (not just one specific campaign)
     * This creates a global waiting period where no campaigns show
     * FIXED: Use UserGlobalPreference instead of fake campaign records
     */
    private void setOneMonthWaitForAllCampaigns(String userId, String companyId, String reason, Date effectiveDate) {
        log.info("Setting 1-month wait period for ALL campaigns for user {}", userId);
        
        // Calculate 1-month from effective date
        Calendar cal = Calendar.getInstance();
        cal.setTime(effectiveDate);
        cal.add(Calendar.MONTH, 1);
        Date waitUntilDate = cal.getTime();
        
        // Use UserGlobalPreference to store global wait period
        UserGlobalPreference preference = globalPreferenceRepository
                .findByUserId(userId)
                .orElse(createNewGlobalPreference(userId));
        
        // Set wait period end date in global preference
        preference.setGlobalWaitUntilDate(waitUntilDate);
        preference.setGlobalWaitReason(reason);
        preference.setUpdatedDate(effectiveDate);
        
        // CRITICAL: Set the flag to remember this user was in wait period
        preference.setHasBeenInWaitPeriod(true);
        
        // Keep insights enabled (this is just a temporary wait, not opt-out)
        if (preference.getInsightsEnabled() == null) {
            preference.setInsightsEnabled(true);
        }
        
        try {
            globalPreferenceRepository.save(preference);
            log.info("Successfully set 1-month wait until {} for ALL campaigns (user {}, company {}) and marked hasBeenInWaitPeriod=true", 
                    waitUntilDate, userId, companyId);
        } catch (Exception e) {
            log.error("Error saving global preference for wait period: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Handle global opt-out (backward compatible)
     */
    @Transactional
    public void handleGlobalOptOut(String userId, String reason) {
        handleGlobalOptOut(userId, reason, new Date());
    }
    
    /**
     * Handle global opt-out with specific date
     */
    @Transactional
    public void handleGlobalOptOut(String userId, String reason, Date effectiveDate) {
        log.info("Processing global opt-out for user: {} at date: {}", userId, effectiveDate);
        
        // Create or update global preference
        UserGlobalPreference preference = globalPreferenceRepository
                .findByUserId(userId)
                .orElse(createNewGlobalPreference(userId));
        
        preference.setInsightsEnabled(false);
        preference.setOptOutDate(effectiveDate);
        preference.setOptOutReason(reason);
        preference.setUpdatedDate(effectiveDate);
        
        globalPreferenceRepository.save(preference);
        
        // Mark all existing closures as opted out
        List<UserInsightClosure> userClosures = closureRepository.findByUserId(userId);
        for (UserInsightClosure closure : userClosures) {
            closure.setOptOutAllInsights(true);
            closure.setUpdatedDate(effectiveDate);
            closureRepository.save(closure);
        }
    }
    
    /**
     * Check if user was previously in 1-month wait period (even if expired now)
     * This determines if user should see SecondClosurePopup for new campaigns after wait period expires
     */
    public boolean wasUserPreviouslyInWaitPeriod(String userId, String companyId, Date checkDate) {
        log.info("=== CHECKING PREVIOUS WAIT PERIOD HISTORY ===");
        log.info("Checking if user {} was previously in wait period for company {} at date {}", userId, companyId, checkDate);
        
        // Check UserGlobalPreference for explicit flag first (most reliable)
        Optional<UserGlobalPreference> prefOpt = globalPreferenceRepository.findByUserId(userId);
        if (prefOpt.isPresent()) {
            UserGlobalPreference pref = prefOpt.get();
            
            // Check explicit flag first
            if (pref.getHasBeenInWaitPeriod() != null && pref.getHasBeenInWaitPeriod()) {
                log.info("User {} has explicit hasBeenInWaitPeriod flag set to true", userId);
                log.info("=== PREVIOUS WAIT PERIOD: TRUE (EXPLICIT FLAG) ===");
                return true;
            }
            
            // Check if user had any wait period (even if expired)
            if (pref.getGlobalWaitUntilDate() != null) {
                // User had a global wait period set at some point (doesn't matter if expired)
                log.info("User {} had global wait period until {} (expired: {})", 
                        userId, pref.getGlobalWaitUntilDate(), 
                        pref.getGlobalWaitUntilDate().before(checkDate));
                
                // Set the flag for future checks if it's not already set
                if (pref.getHasBeenInWaitPeriod() == null || !pref.getHasBeenInWaitPeriod()) {
                    pref.setHasBeenInWaitPeriod(true);
                    globalPreferenceRepository.save(pref);
                    log.info("Updated hasBeenInWaitPeriod flag for user {}", userId);
                }
                
                log.info("=== PREVIOUS WAIT PERIOD: TRUE (GLOBAL PREFERENCE) ===");
                return true;
            }
        }
        
        // Also check individual campaigns for any previous 1-month waits (legacy support)
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId);
        
        for (UserInsightClosure closure : closures) {
            if (closure.getNextEligibleDate() != null) {
                // Check if this was a short-term wait (1-month) vs permanent block (10+ years)
                Calendar farFuture = Calendar.getInstance();
                farFuture.add(Calendar.YEAR, 5); // 5+ years is considered permanent
                boolean isPermanentBlock = closure.getNextEligibleDate().after(farFuture.getTime());
                
                if (!isPermanentBlock) {
                    // This was a 1-month wait period (could be expired or active)
                    log.info("User {} had 1-month wait period until {} for campaign {} (expired: {})", 
                            userId, closure.getNextEligibleDate(), closure.getCampaignId(),
                            closure.getNextEligibleDate().before(checkDate));
                    log.info("=== PREVIOUS WAIT PERIOD: TRUE (CAMPAIGN-SPECIFIC) ===");
                    return true;
                }
            }
            
            // Check closure reason for wait period indicators
            if (closure.getClosureReason() != null && 
                (closure.getClosureReason().contains("1-month wait") || 
                 closure.getClosureReason().contains("month wait") ||
                 closure.getClosureReason().contains("show in future"))) {
                log.info("User {} has closure reason indicating previous wait period: {}", 
                        userId, closure.getClosureReason());
                log.info("=== PREVIOUS WAIT PERIOD: TRUE (REASON TEXT) ===");
                return true;
            }
        }
        
        log.info("User {} has never been in a wait period", userId);
        log.info("=== PREVIOUS WAIT PERIOD: FALSE ===");
        return false;
    }
    
    /**
     * Check if user is in 1-month wait period (should see NO campaigns)
     */
    public boolean isUserInWaitPeriod(String userId, String companyId) {
        return isUserInWaitPeriod(userId, companyId, new Date());
    }
    
    /**
     * Check if user is in 1-month wait period at specific date
     * FIXED: Checks UserGlobalPreference for global waiting period
     */
    public boolean isUserInWaitPeriod(String userId, String companyId, Date checkDate) {
        log.info("=== CHECKING WAIT PERIOD ===");
        log.info("Checking wait period for user {} company {} at date {}", userId, companyId, checkDate);
        
        // Check UserGlobalPreference for global wait period
        Optional<UserGlobalPreference> prefOpt = globalPreferenceRepository.findByUserId(userId);
        if (prefOpt.isPresent()) {
            UserGlobalPreference pref = prefOpt.get();
            if (pref.getGlobalWaitUntilDate() != null && 
                pref.getGlobalWaitUntilDate().after(checkDate)) {
                log.info("User {} in GLOBAL wait period until {} (checked at {})", 
                        userId, pref.getGlobalWaitUntilDate(), checkDate);
                log.info("=== WAIT PERIOD: TRUE (GLOBAL PREFERENCE) ===");
                return true;
            }
        }
        
        // Also check individual campaigns for 1-month waits (legacy support)
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId);
        
        for (UserInsightClosure closure : closures) {
            if (closure.getNextEligibleDate() != null && 
                closure.getNextEligibleDate().after(checkDate)) {
                
                // Check if this is a short-term wait (1-month) vs permanent block (10+ years)
                Calendar farFuture = Calendar.getInstance();
                farFuture.add(Calendar.YEAR, 5); // 5+ years is considered permanent
                boolean isPermanentBlock = closure.getNextEligibleDate().after(farFuture.getTime());
                
                if (!isPermanentBlock) {
                    log.info("User {} in campaign-specific wait period until {} for campaign {} (checked at {})", 
                            userId, closure.getNextEligibleDate(), closure.getCampaignId(), checkDate);
                    log.info("=== WAIT PERIOD: TRUE (CAMPAIGN-SPECIFIC) ===");
                    return true;
                }
            }
        }
        
        log.info("User {} is NOT in wait period", userId);
        log.info("=== WAIT PERIOD: FALSE ===");
        return false;
    }
    
    /**
     * Get all permanently blocked campaigns for a user
     * These campaigns should NEVER be shown again, even after wait period
     */
    public List<String> getPermanentlyBlockedCampaignIds(String userId, String companyId) {
        List<UserInsightClosure> closures = closureRepository
            .findByUserIdAndCompanyId(userId, companyId);
    
        log.info("=== PERMANENT BLOCK CHECK ===");
        log.info("Found {} closure records for user {} company {}", closures.size(), userId, companyId);
    
        List<String> blockedCampaigns = new ArrayList<>();
        
        for (UserInsightClosure closure : closures) {
            log.info("Campaign {}: permanent={}, nextEligible={}, reason={}, closureCount={}", 
                     closure.getCampaignId(), 
                     closure.getPermanentlyClosed(), 
                     closure.getNextEligibleDate(),
                     closure.getClosureReason(),
                     closure.getClosureCount());
            
            // Campaign is permanently blocked if:
            // 1. It's marked as permanently closed
            // 2. OR it has a nextEligibleDate far in the future (10+ years = permanent)
            boolean isPermanentlyClosed = closure.getPermanentlyClosed() != null && closure.getPermanentlyClosed();
            
            boolean hasFarFutureDate = false;
            if (closure.getNextEligibleDate() != null) {
                Calendar farFuture = Calendar.getInstance();
                farFuture.add(Calendar.YEAR, 5); // 5+ years is considered permanent
                hasFarFutureDate = closure.getNextEligibleDate().after(farFuture.getTime());
            }
            
            boolean isBlocked = isPermanentlyClosed || hasFarFutureDate;
            
            if (isBlocked) {
                blockedCampaigns.add(closure.getCampaignId());
                log.info("Campaign {} is PERMANENTLY BLOCKED (permanent={}, farFuture={})", 
                         closure.getCampaignId(), isPermanentlyClosed, hasFarFutureDate);
            } else {
                log.info("Campaign {} is NOT permanently blocked", closure.getCampaignId());
            }
        }
        
        log.info("Total permanently blocked campaigns: {}", blockedCampaigns);
        log.info("=== END PERMANENT BLOCK CHECK ===");
        
        return blockedCampaigns;
    }
    
    /**
     * Check if a campaign is currently closed for a user (backward compatible)
     */
    public boolean isCampaignClosedForUser(String userId, String companyId, String campaignId) {
        return isCampaignClosedForUser(userId, companyId, campaignId, new Date());
    }
    
    /**
     * Check if a campaign is currently closed for a user at specific date
     */
    public boolean isCampaignClosedForUser(String userId, String companyId, String campaignId, Date checkDate) {
        // First check global opt-out
        if (isUserGloballyOptedOut(userId, checkDate)) {
            return true;
        }
        
        // Check if user is in global waiting period
        if (isUserInWaitPeriod(userId, companyId, checkDate)) {
            return true;
        }
        
        Optional<UserInsightClosure> closureOpt = closureRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
        
        if (!closureOpt.isPresent()) {
            return false; // No closure record = not closed
        }
        
        UserInsightClosure closure = closureOpt.get();
        
        // If closure count is 0, not closed
        if (closure.getClosureCount() == 0) {
            return false;
        }
        
        // If permanently blocked (user said "don't see again")
        if (closure.getPermanentlyClosed() != null && closure.getPermanentlyClosed()) {
            log.debug("Campaign {} permanently blocked for user {} (user said 'don't see again')", 
                     campaignId, userId);
            return true; // NEVER show this campaign again
        }
        
        // If has nextEligibleDate far in future (permanent block)
        if (closure.getNextEligibleDate() != null) {
            Calendar farFuture = Calendar.getInstance();
            farFuture.add(Calendar.YEAR, 5);
            if (closure.getNextEligibleDate().after(farFuture.getTime())) {
                log.debug("Campaign {} permanently blocked for user {} (far future date)", 
                         campaignId, userId);
                return true;
            }
        }
        
        // closureCount=2 without response blocks temporarily
        if (closure.getClosureCount() == 2 && 
            closure.getClosureReason() == null &&  // No response given yet
            closure.getPermanentlyClosed() == null &&  // Not decided yet
            closure.getNextEligibleDate() == null) {  // No wait period set
            
            log.debug("Campaign {} temporarily blocked for user {} (waiting for preference response)", 
                     campaignId, userId);
            return true; // Blocked until user responds to preference prompt
        }
        
        // closureCount=1 or user chose "show later" = not blocked
        return false;
    }
    
    /**
     * Get all temporarily closed campaigns for a user at specific date
     * (Does not include permanently blocked campaigns)
     */
    public List<String> getClosedCampaignIds(String userId, String companyId) {
        return getClosedCampaignIds(userId, companyId, new Date());
    }
    
    /**
     * Get all temporarily closed campaigns for a user at specific date
     */
    public List<String> getClosedCampaignIds(String userId, String companyId, Date checkDate) {
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId);
        
        log.info("=== TEMPORARY BLOCK CHECK ===");
        log.info("Checking {} closure records for user {} company {} at date {}", 
                 closures.size(), userId, companyId, checkDate);
        
        List<String> temporarilyClosedCampaignIds = new ArrayList<>();
        
        for (UserInsightClosure closure : closures) {
            log.debug("Checking closure for campaign {}: count={}, permanent={}, nextEligible={}, reason={}", 
                     closure.getCampaignId(), closure.getClosureCount(), 
                     closure.getPermanentlyClosed(), closure.getNextEligibleDate(), closure.getClosureReason());
            
            // Skip permanently blocked campaigns (handled by getPermanentlyBlockedCampaignIds)
            if (closure.getPermanentlyClosed() != null && closure.getPermanentlyClosed()) {
                log.debug("Campaign {} skipped - permanently closed", closure.getCampaignId());
                continue;
            }
            
            // Check for far future nextEligibleDate (permanent block)
            if (closure.getNextEligibleDate() != null) {
                Calendar farFuture = Calendar.getInstance();
                farFuture.add(Calendar.YEAR, 5);
                if (closure.getNextEligibleDate().after(farFuture.getTime())) {
                    log.debug("Campaign {} skipped - far future date (permanent)", closure.getCampaignId());
                    continue;
                }
            }
            
            // Campaign reached closureCount=2 but user hasn't responded yet
            if (closure.getClosureCount() >= 2 && 
                closure.getClosureReason() == null &&  // No response given yet
                closure.getPermanentlyClosed() == null &&  // Not decided yet
                closure.getNextEligibleDate() == null) {  // No wait period set
                
                temporarilyClosedCampaignIds.add(closure.getCampaignId());
                log.debug("Campaign {} blocked - waiting for user response (count={})", 
                         closure.getCampaignId(), closure.getClosureCount());
                continue;
            }
            
            // All other cases: Campaign is ELIGIBLE (unless in global wait period)
            log.debug("Campaign {} is ELIGIBLE for temporary check", closure.getCampaignId());
        }
        
        log.info("Total temporarily blocked campaigns: {}", temporarilyClosedCampaignIds);
        log.info("=== END TEMPORARY BLOCK CHECK ===");
        
        return temporarilyClosedCampaignIds;
    }
    
    /**
     * Check if user has globally opted out (backward compatible)
     */
    public boolean isUserGloballyOptedOut(String userId) {
        return isUserGloballyOptedOut(userId, new Date());
    }
    
    /**
     * Check if user has globally opted out at specific date
     */
    public boolean isUserGloballyOptedOut(String userId, Date checkDate) {
        Optional<UserGlobalPreference> prefOpt = globalPreferenceRepository
                .findByUserId(userId);
        
        if (prefOpt.isPresent()) {
            UserGlobalPreference pref = prefOpt.get();
            // If insights disabled and opt-out date is before or equal to check date
            if (!pref.getInsightsEnabled() && 
                (pref.getOptOutDate() == null || !pref.getOptOutDate().after(checkDate))) {
                log.debug("User {} globally opted out at date {} (opt-out date: {})", 
                         userId, checkDate, pref.getOptOutDate());
                return true;
            }
        }
        
        // Also check if any closure record has global opt-out
        return closureRepository.hasUserOptedOutGlobally(userId);
    }
    
    /**
     * Get user's closure history for a campaign
     */
    public Optional<UserInsightClosure> getUserClosureHistory(String userId, String companyId, String campaignId) {
        return closureRepository.findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
    }
    
    /**
     * Get all closures for a user in a company
     */
    public List<UserInsightClosure> getUserClosures(String userId, String companyId) {
        return closureRepository.findByUserIdAndCompanyId(userId, companyId);
    }
    
    /**
     * Reset closure for a specific campaign (for testing/admin purposes)
     */
    @Transactional
    public void resetCampaignClosure(String userId, String companyId, String campaignId) {
        Optional<UserInsightClosure> closureOpt = closureRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
        
        if (closureOpt.isPresent()) {
            UserInsightClosure closure = closureOpt.get();
            closure.setClosureCount(0);
            closure.setPermanentlyClosed(false);
            closure.setNextEligibleDate(null);
            closure.setClosureReason(null);
            closureRepository.save(closure);
            log.info("Reset closure for user {} campaign {}", userId, campaignId);
        }
    }
    
    /**
     * Get closure statistics for analytics
     */
    public ClosureStatisticsDTO getClosureStatistics(String campaignId) {
        ClosureStatisticsDTO stats = new ClosureStatisticsDTO();
        stats.setCampaignId(campaignId);
        stats.setFirstTimeClosures(closureRepository.countClosuresByCampaign(campaignId, 1));
        stats.setSecondTimeClosures(closureRepository.countClosuresByCampaign(campaignId, 2));
        stats.setMultipleClosures(closureRepository.countClosuresByCampaign(campaignId, 3));
        
        // Calculate closure rate
        List<UserInsightClosure> allClosures = closureRepository.findByCampaignId(campaignId);
        long permanentClosures = allClosures.stream()
                .filter(c -> c.getNextEligibleDate() != null)
                .count();
        stats.setPermanentClosures(permanentClosures);
        
        if (!allClosures.isEmpty()) {
            stats.setClosureRate((double) permanentClosures / allClosures.size() * 100);
        } else {
            stats.setClosureRate(0.0);
        }
        
        return stats;
    }
    
    /**
     * Get campaigns in wait period for a user
     */
    public List<CampaignWaitStatusDTO> getCampaignsInWaitPeriod(String userId, String companyId) {
        List<UserInsightClosure> closures = closureRepository
                .findCampaignsInWaitPeriod(userId, companyId, new Date());
        
        return closures.stream()
                .map(closure -> {
                    CampaignWaitStatusDTO status = new CampaignWaitStatusDTO();
                    status.setCampaignId(closure.getCampaignId());
                    status.setNextEligibleDate(closure.getNextEligibleDate());
                    status.setClosureReason(closure.getClosureReason());
                    status.setClosureCount(closure.getClosureCount());
                    return status;
                })
                .collect(Collectors.toList());
    }
    
    // ===== HELPER METHODS =====
    
    private UserInsightClosure createNewClosure(String userId, String companyId, String campaignId) {
        // SAFETY CHECK: Never create fake campaign records
        if (campaignId.startsWith("GLOBAL_WAIT_")) {
            throw new IllegalArgumentException("Cannot create closure record for fake campaign ID: " + campaignId);
        }
        
        UserInsightClosure closure = new UserInsightClosure();
        closure.setId(UUID.randomUUID().toString());
        closure.setUserId(userId);
        closure.setCompanyId(companyId);
        closure.setCampaignId(campaignId);
        closure.setClosureCount(0);
        closure.setPermanentlyClosed(false);
        closure.setOptOutAllInsights(false);
        return closure;
    }
    
    private UserGlobalPreference createNewGlobalPreference(String userId) {
        UserGlobalPreference pref = new UserGlobalPreference();
        pref.setId(UUID.randomUUID().toString());
        pref.setUserId(userId);
        pref.setInsightsEnabled(true);
        return pref;
    }
    
    // ===== BACKWARD COMPATIBILITY METHODS =====
    
    /**
     * @deprecated Use handlePreferenceResponse instead
     */
    @Transactional
    @Deprecated
    public void handleSecondClosureResponse(String userId, String companyId, String campaignId,
            boolean wantsToSeeAgain, String reason) throws DataHandlingException {
        // Map old method to new method - assume campaign-specific response
        handlePreferenceResponse(userId, companyId, campaignId, wantsToSeeAgain, reason, false, new Date());
    }
    
    /**
     * @deprecated Use handlePreferenceResponse instead  
     */
    @Transactional
    @Deprecated
    public void handleSecondClosureResponse(String userId, String companyId, String campaignId,
            boolean wantsToSeeAgain, String reason, Date effectiveDate) throws DataHandlingException {
        // Map old method to new method - assume campaign-specific response
        handlePreferenceResponse(userId, companyId, campaignId, wantsToSeeAgain, reason, false, effectiveDate);
    }








    /**
 * Check if this is the first closure for a campaign since wait period expired
 * This ensures old campaigns get the "hide then popup" behavior after wait period ends
 */
private boolean isFirstClosureSinceWaitPeriodExpired(String userId, String companyId, 
String campaignId, Date currentDate) {

// Get the user's global preference to find when wait period ended
Optional<UserGlobalPreference> prefOpt = globalPreferenceRepository.findByUserId(userId);
if (!prefOpt.isPresent() || prefOpt.get().getGlobalWaitUntilDate() == null) {
log.debug("No wait period end date found for user {}", userId);
return false;
}

Date waitPeriodEndDate = prefOpt.get().getGlobalWaitUntilDate();

// If we're still in wait period, this doesn't apply
if (waitPeriodEndDate.after(currentDate)) {
log.debug("User {} still in wait period until {}", userId, waitPeriodEndDate);
return false;
}

// Check if this campaign has been closed since wait period ended
Optional<UserInsightClosure> closureOpt = closureRepository
    .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);

if (!closureOpt.isPresent()) {
// No closure record exists - this is definitely first closure since wait period
log.info("No closure record exists for campaign {} - first closure since wait period expired", campaignId);
return true;
}

UserInsightClosure closure = closureOpt.get();

// Check if there's a marker indicating this campaign was closed since wait period expired
// We'll use closureReason field to track this
if (closure.getClosureReason() != null && 
closure.getClosureReason().contains("CLOSED_SINCE_WAIT_PERIOD_EXPIRED")) {
log.debug("Campaign {} already marked as closed since wait period expired", campaignId);
return false;
}

// Check if last closure was before wait period ended
if (closure.getLastClosureDate() != null && closure.getLastClosureDate().before(waitPeriodEndDate)) {
log.info("Campaign {} last closed before wait period ended - treating as first closure since wait period expired", campaignId);
return true;
}

// If last closure was after wait period ended, this is not the first closure
log.debug("Campaign {} was already closed after wait period ended", campaignId);
return false;
}

/**
* Mark a campaign as having been closed since wait period expired
* This prevents the "first closure" behavior from happening multiple times
*/
private void markCampaignClosedSinceWaitPeriodExpired(String userId, String companyId, 
String campaignId, Date currentDate) {

Optional<UserInsightClosure> closureOpt = closureRepository
    .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);

if (closureOpt.isPresent()) {
UserInsightClosure closure = closureOpt.get();

// Add marker to closure reason to indicate this campaign was closed since wait period expired
String existingReason = closure.getClosureReason();
String newReason = (existingReason != null ? existingReason + "; " : "") + 
                  "CLOSED_SINCE_WAIT_PERIOD_EXPIRED on " + currentDate;

closure.setClosureReason(newReason);
closure.setUpdatedDate(currentDate);

closureRepository.save(closure);

log.info("Marked campaign {} as closed since wait period expired for user {}", campaignId, userId);
}
}





private boolean isPostWaitPeriodCampaignHandled(String userId, String companyId, 
        String campaignId, Date currentDate) {
    
    try {
        Optional<UserGlobalPreference> prefOpt = globalPreferenceRepository.findByUserId(userId);
        if (!prefOpt.isPresent()) {
            return false;
        }
        
        UserGlobalPreference pref = prefOpt.get();
        
        // Check if we're still in wait period
        Date waitPeriodEndDate = pref.getGlobalWaitUntilDate();
        if (waitPeriodEndDate == null || waitPeriodEndDate.after(currentDate)) {
            return false; // Still in wait period or no wait period
        }
        
        // FIXED: Get all closure records for this user/company and filter by campaign
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .stream()
                .filter(c -> c.getCampaignId().equals(campaignId))
                .collect(Collectors.toList());
        
        if (closures.isEmpty()) {
            return false; // No closure record
        }
        
        if (closures.size() > 1) {
            log.warn("Multiple closure records found for campaign {} user {}. Checking all for post-wait-period marker.", 
                    campaignId, userId);
        }
        
        // Check if ANY of the closure records has the post-wait-period marker
        for (UserInsightClosure closure : closures) {
            if (closure.getClosureReason() != null && 
                closure.getClosureReason().contains("POST_WAIT_PERIOD_HANDLED")) {
                log.debug("Campaign {} already handled in post-wait-period mode for user {}", campaignId, userId);
                return true;
            }
        }
        
        return false;
        
    } catch (Exception e) {
        log.error("Error checking if campaign {} is post-wait-period handled for user {}: {}", 
                campaignId, userId, e.getMessage(), e);
        // Default to false if there's an error
        return false;
    }
}

/**
 * Mark a campaign as handled in post-wait-period mode
 */
private void markPostWaitPeriodCampaignHandled(String userId, String companyId, 
        String campaignId, Date currentDate) {
    
    try {
        // CRITICAL FIX: Use findFirst to handle potential duplicates gracefully
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .stream()
                .filter(c -> c.getCampaignId().equals(campaignId))
                .collect(Collectors.toList());
        
        if (closures.isEmpty()) {
            log.error("No closure record found for campaign {} when trying to mark as post-wait-period handled", campaignId);
            return;
        }
        
        if (closures.size() > 1) {
            log.warn("Multiple closure records found for campaign {}. Using the most recent one.", campaignId);
            // Use the one with the most recent update date
            closures.sort((c1, c2) -> c2.getUpdatedDate().compareTo(c1.getUpdatedDate()));
        }
        
        UserInsightClosure closure = closures.get(0);
        
        // Add marker to indicate this campaign was handled in post-wait-period mode
        String existingReason = closure.getClosureReason();
        String newReason = (existingReason != null ? existingReason + "; " : "") + 
                          "POST_WAIT_PERIOD_HANDLED on " + currentDate;
        
        closure.setClosureReason(newReason);
        closure.setUpdatedDate(currentDate);
        
        closureRepository.save(closure);
        
        log.info("Marked campaign {} as handled in post-wait-period mode for user {}", campaignId, userId);
        
    } catch (Exception e) {
        log.error("Error marking campaign {} as post-wait-period handled for user {}: {}", 
                campaignId, userId, e.getMessage(), e);
        // Don't throw exception - this is not critical for the main flow
    }
}



@Transactional
public void cleanupDuplicateClosureRecords(String userId, String companyId, String campaignId) {
    try {
        log.info("Cleaning up duplicate closure records for user: {}, company: {}, campaign: {}", 
                userId, companyId, campaignId);
        
        List<UserInsightClosure> allClosures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .stream()
                .filter(c -> c.getCampaignId().equals(campaignId))
                .collect(Collectors.toList());
        
        if (allClosures.size() <= 1) {
            log.info("No duplicates found for campaign {}", campaignId);
            return;
        }
        
        log.warn("Found {} duplicate closure records for campaign {}. Merging them.", 
                allClosures.size(), campaignId);
        
        // Sort by update date (most recent first)
        allClosures.sort((c1, c2) -> {
            Date d1 = c1.getUpdatedDate() != null ? c1.getUpdatedDate() : c1.getLastClosureDate();
            Date d2 = c2.getUpdatedDate() != null ? c2.getUpdatedDate() : c2.getLastClosureDate();
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            return d2.compareTo(d1); // Most recent first
        });
        
        // Keep the most recent record, merge data from others
        UserInsightClosure primaryRecord = allClosures.get(0);
        
        // Merge data from other records
        int maxClosureCount = primaryRecord.getClosureCount();
        Date earliestFirstClosure = primaryRecord.getFirstClosureDate();
        Date latestClosure = primaryRecord.getLastClosureDate();
        String combinedReason = primaryRecord.getClosureReason();
        Boolean permanentlyClosed = primaryRecord.getPermanentlyClosed();
        Date nextEligibleDate = primaryRecord.getNextEligibleDate();
        
        for (int i = 1; i < allClosures.size(); i++) {
            UserInsightClosure duplicate = allClosures.get(i);
            
            // Take the highest closure count
            if (duplicate.getClosureCount() > maxClosureCount) {
                maxClosureCount = duplicate.getClosureCount();
            }
            
            // Take the earliest first closure date
            if (duplicate.getFirstClosureDate() != null && 
                (earliestFirstClosure == null || duplicate.getFirstClosureDate().before(earliestFirstClosure))) {
                earliestFirstClosure = duplicate.getFirstClosureDate();
            }
            
            // Take the latest closure date
            if (duplicate.getLastClosureDate() != null && 
                (latestClosure == null || duplicate.getLastClosureDate().after(latestClosure))) {
                latestClosure = duplicate.getLastClosureDate();
            }
            
            // Combine closure reasons
            if (duplicate.getClosureReason() != null && 
                (combinedReason == null || !combinedReason.contains(duplicate.getClosureReason()))) {
                combinedReason = (combinedReason != null ? combinedReason + "; " : "") + duplicate.getClosureReason();
            }
            
            // Take permanent closed if any record has it
            if (duplicate.getPermanentlyClosed() != null && duplicate.getPermanentlyClosed()) {
                permanentlyClosed = true;
            }
            
            // Take the later next eligible date (more restrictive)
            if (duplicate.getNextEligibleDate() != null && 
                (nextEligibleDate == null || duplicate.getNextEligibleDate().after(nextEligibleDate))) {
                nextEligibleDate = duplicate.getNextEligibleDate();
            }
        }
        
        // Update primary record with merged data
        primaryRecord.setClosureCount(maxClosureCount);
        primaryRecord.setFirstClosureDate(earliestFirstClosure);
        primaryRecord.setLastClosureDate(latestClosure);
        primaryRecord.setClosureReason(combinedReason);
        primaryRecord.setPermanentlyClosed(permanentlyClosed);
        primaryRecord.setNextEligibleDate(nextEligibleDate);
        primaryRecord.setUpdatedDate(new Date());
        
        // Save the merged primary record
        closureRepository.save(primaryRecord);
        
        // Delete the duplicate records
        for (int i = 1; i < allClosures.size(); i++) {
            closureRepository.delete(allClosures.get(i));
            log.info("Deleted duplicate closure record {} for campaign {}", 
                    allClosures.get(i).getId(), campaignId);
        }
        
        log.info("Successfully cleaned up duplicates for campaign {}. Merged into record: {}", 
                campaignId, primaryRecord.getId());
        
    } catch (Exception e) {
        log.error("Error cleaning up duplicate closure records for campaign {}: {}", 
                campaignId, e.getMessage(), e);
        // Don't throw - this is cleanup, not critical
    }
}

/**
 * Check for and clean up any duplicate records before processing
 * Call this at the beginning of recordInsightClosure
 */
private void ensureNoDuplicateRecords(String userId, String companyId, String campaignId) {
    try {
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId)
                .stream()
                .filter(c -> c.getCampaignId().equals(campaignId))
                .collect(Collectors.toList());
        
        if (closures.size() > 1) {
            log.warn("Detected {} duplicate closure records for campaign {}. Cleaning up.", 
                    closures.size(), campaignId);
            cleanupDuplicateClosureRecords(userId, companyId, campaignId);
        }
    } catch (Exception e) {
        log.error("Error checking for duplicate records: {}", e.getMessage(), e);
        // Continue processing even if cleanup fails
    }
}










private int getVirtualClosureCountForPostWaitPeriod(String userId, String companyId, 
        String campaignId, Date currentDate) {
    
    try {
        // Get the wait period end date to know when virtual counting started
        Optional<UserGlobalPreference> prefOpt = globalPreferenceRepository.findByUserId(userId);
        if (!prefOpt.isPresent() || prefOpt.get().getGlobalWaitUntilDate() == null) {
            return 0;
        }
        
        Date waitPeriodEndDate = prefOpt.get().getGlobalWaitUntilDate();
        
        // Store virtual closure count in UserGlobalPreference as JSON-like string
        // Format: "VIRTUAL_CLOSURES:{campaignId1}:2,{campaignId2}:1"
        UserGlobalPreference pref = prefOpt.get();
        String virtualClosureData = pref.getGlobalWaitReason(); // Reuse this field for virtual data
        
        if (virtualClosureData == null || !virtualClosureData.contains("VIRTUAL_CLOSURES:")) {
            return 0; // No virtual closures recorded yet
        }
        
        // Parse virtual closure count for this campaign
        String searchPattern = campaignId + ":";
        int startIndex = virtualClosureData.indexOf(searchPattern);
        if (startIndex == -1) {
            return 0; // This campaign not found in virtual data
        }
        
        startIndex += searchPattern.length();
        int endIndex = virtualClosureData.indexOf(",", startIndex);
        if (endIndex == -1) {
            endIndex = virtualClosureData.length();
        }
        
        String countStr = virtualClosureData.substring(startIndex, endIndex);
        return Integer.parseInt(countStr);
        
    } catch (Exception e) {
        log.error("Error getting virtual closure count for campaign {}: {}", campaignId, e.getMessage());
        return 0; // Default to 0 if any error
    }
}

/**
 * Increment virtual closure count for a campaign after wait period
 */
private void incrementVirtualClosureCount(String userId, String companyId, 
        String campaignId, Date currentDate) {
    
    try {
        Optional<UserGlobalPreference> prefOpt = globalPreferenceRepository.findByUserId(userId);
        if (!prefOpt.isPresent()) {
            return;
        }
        
        UserGlobalPreference pref = prefOpt.get();
        String virtualClosureData = pref.getGlobalWaitReason();
        
        if (virtualClosureData == null || !virtualClosureData.contains("VIRTUAL_CLOSURES:")) {
            // Initialize virtual closure data
            virtualClosureData = "VIRTUAL_CLOSURES:" + campaignId + ":1";
        } else {
            // Update existing virtual closure data
            String searchPattern = campaignId + ":";
            int startIndex = virtualClosureData.indexOf(searchPattern);
            
            if (startIndex == -1) {
                // Add new campaign to virtual data
                virtualClosureData += "," + campaignId + ":1";
            } else {
                // Update existing campaign count
                startIndex += searchPattern.length();
                int endIndex = virtualClosureData.indexOf(",", startIndex);
                if (endIndex == -1) {
                    endIndex = virtualClosureData.length();
                }
                
                String countStr = virtualClosureData.substring(startIndex, endIndex);
                int currentCount = Integer.parseInt(countStr);
                int newCount = currentCount + 1;
                
                virtualClosureData = virtualClosureData.substring(0, startIndex) + 
                                   newCount + 
                                   virtualClosureData.substring(endIndex);
            }
        }
        
        pref.setGlobalWaitReason(virtualClosureData);
        pref.setUpdatedDate(currentDate);
        globalPreferenceRepository.save(pref);
        
        log.info("Updated virtual closure count for campaign {}: {}", campaignId, virtualClosureData);
        
    } catch (Exception e) {
        log.error("Error incrementing virtual closure count for campaign {}: {}", campaignId, e.getMessage());
    }
}

/**
 * Reset virtual closure counts when a new wait period starts
 * Call this when user enters a new wait period
 */
public void resetVirtualClosureCountsForNewWaitPeriod(String userId, Date effectiveDate) {
    try {
        Optional<UserGlobalPreference> prefOpt = globalPreferenceRepository.findByUserId(userId);
        if (prefOpt.isPresent()) {
            UserGlobalPreference pref = prefOpt.get();
            
            // Clear virtual closure data when new wait period starts
            String currentReason = pref.getGlobalWaitReason();
            if (currentReason != null && currentReason.contains("VIRTUAL_CLOSURES:")) {
                // Extract the original reason before virtual data
                int virtualIndex = currentReason.indexOf("VIRTUAL_CLOSURES:");
                String originalReason = virtualIndex > 0 ? currentReason.substring(0, virtualIndex).trim() : "";
                if (originalReason.endsWith(";")) {
                    originalReason = originalReason.substring(0, originalReason.length() - 1);
                }
                pref.setGlobalWaitReason(originalReason);
            }
            
            pref.setUpdatedDate(effectiveDate);
            globalPreferenceRepository.save(pref);
            
            log.info("Reset virtual closure counts for user {} at start of new wait period", userId);
        }
    } catch (Exception e) {
        log.error("Error resetting virtual closure counts for user {}: {}", userId, e.getMessage());
    }
}


}