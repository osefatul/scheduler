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
     * FIXED: Proper logic for weekly rotation system
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
            // SECOND CLOSURE: Check if user is already in waiting period to determine prompt type
            log.info("SECOND closure for campaign {} at date {}. Checking if user is in waiting period.", 
                    campaignId, effectiveDate);
            
            // CRITICAL FIX: Check if user is currently in waiting period (not just other campaign closures)
            boolean isInWaitPeriod = isUserInWaitPeriod(userId, companyId, effectiveDate);
            
            if (isInWaitPeriod) {
                // User is already in waiting period - ask about ALL future insights (global prompt)
                log.info("User {} is in waiting period. Showing global prompt for campaign {}", 
                        userId, campaignId);
                response.setAction("PROMPT_GLOBAL_PREFERENCE");
                response.setMessage("You've closed multiple campaigns recently. Would you like to see future insights/campaigns?");
                response.setRequiresUserInput(true);
                response.setIsGlobalPrompt(true);
            } else {
                // User is NOT in waiting period - ask about THIS campaign only (first closure prompt)
                log.info("User {} is NOT in waiting period. Showing campaign-specific prompt for campaign {}", 
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
     * REMOVED: hasUserClosedOtherCampaigns method since it was causing the issue
     * The logic now properly checks if user is in waiting period instead
     */
    
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
     */
    @Transactional
    public void handlePreferenceResponse(String userId, String companyId, String campaignId,
            boolean wantsToSee, String reason, boolean isGlobalResponse, Date effectiveDate) 
            throws DataHandlingException {

        log.info("Handling preference response for user: {}, campaign: {}, wantsToSee: {}, isGlobal: {} at date: {}", 
                userId, campaignId, wantsToSee, isGlobalResponse, effectiveDate);

        if (isGlobalResponse) {
            // This is response to "see future insights?" prompt (global)
            if (wantsToSee) {
                // User wants to see future insights - set 1-month wait for THIS campaign only
                log.info("User wants future insights. Setting 1-month wait for campaign {}", campaignId);
                setOneMonthWaitForCampaign(userId, companyId, campaignId, reason, effectiveDate);
            } else {
                // User doesn't want future insights - GLOBAL OPT-OUT
                log.info("User doesn't want future insights. Global opt-out for user {}", userId);
                handleGlobalOptOut(userId, reason, effectiveDate);
            }
        } else {
            // This is response to "see this campaign again?" prompt (campaign-specific)
            UserInsightClosure closure = closureRepository
                    .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId)
                    .orElseThrow(() -> new DataHandlingException(HttpStatus.NOT_FOUND.toString(),
                            "No closure record found"));
            
            log.info("=== PREFERENCE RESPONSE DEBUG ===");
            log.info("BEFORE: closureCount={}, permanent={}, nextEligible={}, reason={}", 
                     closure.getClosureCount(), closure.getPermanentlyClosed(), 
                     closure.getNextEligibleDate(), closure.getClosureReason());
            
            if (wantsToSee) {
                // User wants to see THIS campaign again - mark as eligible
                log.info("User wants to see campaign {} again. Marking as eligible.", campaignId);
                
                // Keep closure count, clear blocking flags
                closure.setPermanentlyClosed(false);
                closure.setNextEligibleDate(null);
                closure.setClosureReason(reason != null ? reason : "User chose to see campaign again");
                
            } else {
                // User doesn't want THIS campaign - permanently block it
                log.info("User doesn't want campaign {}. Setting permanent block.", campaignId);
                
                closure.setClosureReason(reason != null ? reason : "User chose not to see campaign again");
                closure.setPermanentlyClosed(true);  // This should block the campaign
                
                // Set nextEligibleDate far in future for extra safety
                Calendar cal = Calendar.getInstance();
                cal.setTime(effectiveDate);
                cal.add(Calendar.YEAR, 10);  // 10 years in future = effectively permanent
                closure.setNextEligibleDate(cal.getTime());
                
                log.info("Campaign {} permanently blocked with nextEligibleDate: {}", 
                         campaignId, closure.getNextEligibleDate());
            }
            
            closure.setUpdatedDate(effectiveDate);
            UserInsightClosure saved = closureRepository.save(closure);
            
            log.info("AFTER SAVE: closureCount={}, permanent={}, nextEligible={}, reason={}", 
                     saved.getClosureCount(), saved.getPermanentlyClosed(), 
                     saved.getNextEligibleDate(), saved.getClosureReason());
            log.info("=== PREFERENCE RESPONSE COMPLETE ===");
        }
    }
    
    /**
     * Set 1-month wait for a specific campaign
     */
    private void setOneMonthWaitForCampaign(String userId, String companyId, String campaignId, 
            String reason, Date effectiveDate) {
        UserInsightClosure closure = closureRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId)
                .orElseThrow(() -> new RuntimeException("Closure record not found"));
        
        closure.setClosureReason(reason);
        
        Calendar cal = Calendar.getInstance();
        cal.setTime(effectiveDate);
        cal.add(Calendar.MONTH, 1);
        closure.setNextEligibleDate(cal.getTime());
        closure.setUpdatedDate(effectiveDate);
        
        closureRepository.save(closure);
        log.info("Set 1-month wait until {} for campaign {}", closure.getNextEligibleDate(), campaignId);
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
     * Check if user is in 1-month wait period (should see NO campaigns)
     */
    public boolean isUserInWaitPeriod(String userId, String companyId) {
        return isUserInWaitPeriod(userId, companyId, new Date());
    }
    
    /**
     * Check if user is in 1-month wait period at specific date
     * During wait period, user should see NO campaigns at all
     * 
     * FIXED: This method now properly determines if user should see global prompt
     */
    public boolean isUserInWaitPeriod(String userId, String companyId, Date checkDate) {
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId);
        
        log.info("=== CHECKING WAIT PERIOD ===");
        log.info("Checking {} closure records for user {} company {} at date {}", 
                 closures.size(), userId, companyId, checkDate);
        
        for (UserInsightClosure closure : closures) {
            log.debug("Closure for campaign {}: nextEligible={}, permanent={}", 
                     closure.getCampaignId(), closure.getNextEligibleDate(), closure.getPermanentlyClosed());
            
            if (closure.getNextEligibleDate() != null && 
                closure.getNextEligibleDate().after(checkDate)) {
                
                // Check if this is a short-term wait (1-month) vs permanent block (10+ years)
                Calendar farFuture = Calendar.getInstance();
                farFuture.add(Calendar.YEAR, 5); // 5+ years is considered permanent
                boolean isPermanentBlock = closure.getNextEligibleDate().after(farFuture.getTime());
                
                if (!isPermanentBlock) {
                    log.info("User {} in 1-month wait period until {} (checked at {})", 
                            userId, closure.getNextEligibleDate(), checkDate);
                    log.info("=== WAIT PERIOD: TRUE ===");
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
        
        // If has nextEligibleDate, check if it's in the future
        if (closure.getNextEligibleDate() != null && closure.getNextEligibleDate().after(checkDate)) {
            log.debug("Campaign {} blocked for user {} until {} (waiting period or permanent)", 
                     campaignId, userId, closure.getNextEligibleDate());
            return true;
        }
        
        // CRITICAL CHANGE: closureCount=1 does NOT block campaign
        // Only closureCount=2 blocks (waiting for user preference response)
        if (closure.getClosureCount() == 2 && 
            closure.getClosureReason() == null &&  // No response given yet
            closure.getPermanentlyClosed() == null &&  // Not decided yet
            closure.getNextEligibleDate() == null) {  // No wait period set
            
            log.debug("Campaign {} temporarily blocked for user {} (waiting for preference response)", 
                     campaignId, userId);
            return true; // Blocked until user responds to preference prompt
        }
        
        // closureCount=1 or reset to 0 = not blocked
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
            
            // 1. Campaign has short-term wait period (1-month wait from global preference)
            if (closure.getNextEligibleDate() != null && closure.getNextEligibleDate().after(checkDate)) {
                temporarilyClosedCampaignIds.add(closure.getCampaignId());
                log.debug("Campaign {} blocked - in wait period until {}", 
                         closure.getCampaignId(), closure.getNextEligibleDate());
                continue;
            }
            
            // 2. Campaign reached closureCount=2 but user hasn't responded yet
            if (closure.getClosureCount() >= 2 && 
                closure.getClosureReason() == null &&  // No response given yet
                closure.getPermanentlyClosed() == null &&  // Not decided yet
                closure.getNextEligibleDate() == null) {  // No wait period set
                
                temporarilyClosedCampaignIds.add(closure.getCampaignId());
                log.debug("Campaign {} blocked - waiting for user response (count={})", 
                         closure.getCampaignId(), closure.getClosureCount());
                continue;
            }
            
            // All other cases: Campaign is ELIGIBLE
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