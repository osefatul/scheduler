package com.usbank.corp.dcr.api.service;

import com.usbank.corp.dcr.api.entity.*;
import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.model.*;
import com.usbank.corp.dcr.api.repository.*;
import lombok.extern.slf4j.Slf4j;
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

        // Find or create closure record
        UserInsightClosure closure = closureRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId)
                .orElse(createNewClosure(userId, companyId, campaignId));

        // Increment closure count
        closure.setClosureCount(closure.getClosureCount() + 1);
        closure.setLastClosureDate(effectiveDate);

        InsightClosureResponseDTO response = new InsightClosureResponseDTO();
        response.setCampaignId(campaignId);
        response.setClosureCount(closure.getClosureCount());
        response.setEffectiveDate(effectiveDate);

        if (closure.getClosureCount() == 1) {
            // FIRST CLOSURE: Record closure but DO NOT block campaign
            log.info("FIRST closure for campaign {} at date {}. Campaign remains eligible based on normal rules.", 
                    campaignId, effectiveDate);
            closure.setFirstClosureDate(effectiveDate);
            response.setAction("RECORDED_FIRST_CLOSURE");
            response.setMessage("Closure recorded. Campaign remains available based on normal eligibility rules.");
            response.setRequiresUserInput(false);
            
        } else if (closure.getClosureCount() == 2) {
            // SECOND CLOSURE: Check if user was ever in waiting period to determine popup type
            log.info("SECOND closure for campaign {} at date {}. Checking user's waiting period history.", 
                    campaignId, effectiveDate);
            
            // CRITICAL: Check if user is currently in waiting period OR was previously in waiting period
            boolean isCurrentlyInWaitPeriod = isUserInWaitPeriod(userId, companyId, effectiveDate);
            boolean wasPreviouslyInWaitPeriod = wasUserPreviouslyInWaitPeriod(userId, companyId, effectiveDate);
            
            if (isCurrentlyInWaitPeriod || wasPreviouslyInWaitPeriod) {
                // User is in waiting period OR was previously in waiting period
                // Show SECOND closure popup (global options)
                log.info("User {} was previously in waiting period or is currently in one. Showing SecondClosurePopup for campaign {}", 
                        userId, campaignId);
                response.setAction("PROMPT_GLOBAL_PREFERENCE");
                response.setMessage("You've closed campaigns recently. Want to stop seeing these?");
                response.setRequiresUserInput(true);
                response.setIsGlobalPrompt(true);
            } else {
                // User has never been in waiting period - show FIRST closure popup
                log.info("User {} has never been in waiting period. Showing FirstClosurePopup for campaign {}", 
                        userId, campaignId);
                response.setAction("PROMPT_CAMPAIGN_PREFERENCE");
                response.setMessage("Would you like to see this campaign again in the future?");
                response.setRequiresUserInput(true);
                response.setIsGlobalPrompt(false);
            }
            
        } else {
            // MULTIPLE CLOSURES: Should not happen with correct logic
            log.warn("Multiple closures ({}) for campaign {} - this shouldn't happen", 
                    closure.getClosureCount(), campaignId);
            response.setAction("UNEXPECTED_MULTIPLE_CLOSURE");
            response.setMessage("Unexpected closure count. Please contact support.");
        }

        closure.setUpdatedDate(effectiveDate);
        closureRepository.save(closure);
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
        
        // Keep insights enabled (this is just a temporary wait, not opt-out)
        if (preference.getInsightsEnabled() == null) {
            preference.setInsightsEnabled(true);
        }
        
        try {
            globalPreferenceRepository.save(preference);
            log.info("Successfully set 1-month wait until {} for ALL campaigns (user {}, company {}) using UserGlobalPreference", 
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
        log.info("Checking if user {} was previously in wait period for company {} before date {}", userId, companyId, checkDate);
        
        // Check UserGlobalPreference for any previous global wait period
        Optional<UserGlobalPreference> prefOpt = globalPreferenceRepository.findByUserId(userId);
        if (prefOpt.isPresent()) {
            UserGlobalPreference pref = prefOpt.get();
            if (pref.getGlobalWaitUntilDate() != null) {
                // User had a global wait period set at some point
                log.info("User {} had global wait period until {} (expired: {})", 
                        userId, pref.getGlobalWaitUntilDate(), 
                        pref.getGlobalWaitUntilDate().before(checkDate));
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
}