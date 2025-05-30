// UserInsightClosureService.java
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
    
    private static final long ONE_MONTH_MILLIS = 30L * 24L * 60L * 60L * 1000L; // 30 days in milliseconds
    
    @Autowired
    private UserInsightClosureRepository closureRepository;
    
    @Autowired
    private UserGlobalPreferenceRepository globalPreferenceRepository;
    
    @Autowired
    private UserCampaignTrackerRepository userTrackerRepository;
    
    /**
     * Record an insight closure by a user
     */
    @Transactional
    public InsightClosureResponseDTO recordInsightClosure(String userId, String companyId, 
            String campaignId) throws DataHandlingException {
        
        log.info("Recording insight closure for user: {}, company: {}, campaign: {}", 
                userId, companyId, campaignId);
        
        // Check if user has global opt-out
        if (isUserGloballyOptedOut(userId)) {
            throw new DataHandlingException(HttpStatus.FORBIDDEN.toString(), 
                    "User has opted out of all insights");
        }
        
        // Find or create closure record
        UserInsightClosure closure = closureRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId)
                .orElse(createNewClosure(userId, companyId, campaignId));
        
        // Increment closure count
        closure.setClosureCount(closure.getClosureCount() + 1);
        closure.setLastClosureDate(new Date());
        
        InsightClosureResponseDTO response = new InsightClosureResponseDTO();
        response.setCampaignId(campaignId);
        response.setClosureCount(closure.getClosureCount());
        
        if (closure.getClosureCount() == 1) {
            // First closure - just hide until next eligibility
            log.info("First closure for campaign {}. Hiding until next eligibility.", campaignId);
            closure.setFirstClosureDate(new Date());
            response.setAction("HIDDEN_UNTIL_NEXT_ELIGIBILITY");
            response.setMessage("This insight will be hidden until you're next eligible to see it.");
            
        } else if (closure.getClosureCount() == 2) {
            // Second closure - ask if they want to see it again
            log.info("Second closure for campaign {}. Prompting user preference.", campaignId);
            response.setAction("PROMPT_USER_PREFERENCE");
            response.setMessage("Would you like to see this insight again in the future?");
            response.setRequiresUserInput(true);
            
        } else {
            // Multiple closures - check if we should ask about global opt-out
            log.info("Multiple closures ({}) for campaign {}.", closure.getClosureCount(), campaignId);
            response.setAction("CONSIDER_GLOBAL_OPTOUT");
            response.setMessage("You've closed this insight multiple times. Would you like to stop seeing all insights?");
            response.setRequiresUserInput(true);
        }
        
        closureRepository.save(closure);
        return response;
    }
    
    /**
     * Handle user's response to "see again?" prompt
     */
    @Transactional
    public void handleSecondClosureResponse(String userId, String companyId, String campaignId,
            boolean wantsToSeeAgain, String reason) throws DataHandlingException {
        
        UserInsightClosure closure = closureRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId)
                .orElseThrow(() -> new DataHandlingException(HttpStatus.NOT_FOUND.toString(),
                        "No closure record found"));
        
        if (wantsToSeeAgain) {
            // User wants to see it again - reset temporary closure
            log.info("User wants to see campaign {} again. Resetting closure status.", campaignId);
            // Keep the closure count but allow normal rotation
            closure.setPermanentlyClosed(false);
            closure.setNextEligibleDate(null);
        } else {
            // User doesn't want to see it - set one month wait
            log.info("User doesn't want to see campaign {} again. Setting 1 month wait.", campaignId);
            closure.setClosureReason(reason);
            closure.setNextEligibleDate(new Date(System.currentTimeMillis() + ONE_MONTH_MILLIS));
            
            // Check if this is part of a pattern - multiple campaigns closed
            checkForGlobalOptOutPattern(userId, companyId);
        }
        
        closureRepository.save(closure);
    }
    
    /**
     * Handle global opt-out request
     */
    @Transactional
    public void handleGlobalOptOut(String userId, String reason) {
        log.info("Processing global opt-out for user: {}", userId);
        
        // Create or update global preference
        UserGlobalPreference preference = globalPreferenceRepository
                .findByUserId(userId)
                .orElse(createNewGlobalPreference(userId));
        
        preference.setInsightsEnabled(false);
        preference.setOptOutDate(new Date());
        preference.setOptOutReason(reason);
        
        globalPreferenceRepository.save(preference);
        
        // Mark all existing closures as opted out
        List<UserInsightClosure> userClosures = closureRepository
                .findByUserId(userId);
        for (UserInsightClosure closure : userClosures) {
            closure.setOptOutAllInsights(true);
            closureRepository.save(closure);
        }
    }
    
    /**
     * Re-enable insights for a user (opt back in)
     */
    @Transactional
    public void enableInsights(String userId) {
        log.info("Re-enabling insights for user: {}", userId);
        
        UserGlobalPreference preference = globalPreferenceRepository
                .findByUserId(userId)
                .orElse(createNewGlobalPreference(userId));
        
        preference.setInsightsEnabled(true);
        preference.setOptOutDate(null);
        preference.setOptOutReason(null);
        
        globalPreferenceRepository.save(preference);
        
        // Update all closure records
        List<UserInsightClosure> userClosures = closureRepository
                .findByUserId(userId);
        for (UserInsightClosure closure : userClosures) {
            closure.setOptOutAllInsights(false);
            closureRepository.save(closure);
        }
    }
    
    /**
     * Check if user should be prompted for global opt-out
     */
    private void checkForGlobalOptOutPattern(String userId, String companyId) {
        // Count how many campaigns user has closed multiple times
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId);
        
        long multipleClosureCount = closures.stream()
                .filter(c -> c.getClosureCount() >= 2 && !c.getPermanentlyClosed())
                .count();
        
        if (multipleClosureCount >= 3) {
            log.info("User {} has closed {} campaigns multiple times. Consider global opt-out prompt.", 
                    userId, multipleClosureCount);
            // This information can be used by the controller to trigger global opt-out prompt
        }
    }
    
    /**
     * Check if a campaign is currently closed for a user
     */
    public boolean isCampaignClosedForUser(String userId, String companyId, String campaignId) {
        Optional<UserInsightClosure> closureOpt = closureRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
        
        if (!closureOpt.isPresent()) {
            return false;
        }
        
        UserInsightClosure closure = closureOpt.get();
        
        // Check if permanently closed
        if (closure.getPermanentlyClosed()) {
            return true;
        }
        
        // Check if in wait period
        if (closure.getNextEligibleDate() != null && 
            closure.getNextEligibleDate().after(new Date())) {
            return true;
        }
        
        // Check if temporarily closed (first closure)
        if (closure.getClosureCount() == 1 && closure.getLastClosureDate() != null) {
            // This is hidden until next normal eligibility
            // The rotation service will handle this
            return true;
        }
        
        return false;
    }
    
    /**
     * Get all closed campaigns for a user
     */
    public List<String> getClosedCampaignIds(String userId, String companyId) {
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId);
        
        List<String> closedCampaignIds = new ArrayList<>();
        Date now = new Date();
        
        for (UserInsightClosure closure : closures) {
            if (closure.getPermanentlyClosed() ||
                (closure.getNextEligibleDate() != null && closure.getNextEligibleDate().after(now)) ||
                (closure.getClosureCount() > 0 && !isEligibleAfterClosure(closure))) {
                closedCampaignIds.add(closure.getCampaignId());
            }
        }
        
        return closedCampaignIds;
    }
    
    /**
     * Check if user is eligible to see a campaign after closure
     */
    private boolean isEligibleAfterClosure(UserInsightClosure closure) {
        // For first closure, check if they've had a different campaign since
        if (closure.getClosureCount() == 1) {
            // This logic would check with UserCampaignTracker to see if user
            // has viewed other campaigns since this closure
            return hasViewedOtherCampaignsSince(closure.getUserId(), 
                    closure.getCompanyId(), closure.getCampaignId(), 
                    closure.getLastClosureDate());
        }
        return true;
    }
    
    /**
     * Check if user has viewed other campaigns since a date
     */
    private boolean hasViewedOtherCampaignsSince(String userId, String companyId, 
            String excludeCampaignId, Date sinceDate) {
        List<UserCampaignTracker> trackers = userTrackerRepository
                .findRecentByUserIdAndCompanyId(userId, companyId);
        
        return trackers.stream()
                .anyMatch(t -> !t.getCampaignId().equals(excludeCampaignId) &&
                        t.getLastViewDate() != null &&
                        t.getLastViewDate().after(sinceDate));
    }
    
    /**
     * Check if user has globally opted out
     */
    public boolean isUserGloballyOptedOut(String userId) {
        Optional<UserGlobalPreference> prefOpt = globalPreferenceRepository
                .findByUserId(userId);
        
        if (prefOpt.isPresent()) {
            return !prefOpt.get().getInsightsEnabled();
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
     * Reset closure for a specific campaign
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
        }
    }
    
    /**
     * Create new closure record
     */
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
    
    /**
     * Create new global preference
     */
    private UserGlobalPreference createNewGlobalPreference(String userId) {
        UserGlobalPreference pref = new UserGlobalPreference();
        pref.setId(UUID.randomUUID().toString());
        pref.setUserId(userId);
        pref.setInsightsEnabled(true);
        return pref;
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
                .filter(UserInsightClosure::getPermanentlyClosed)
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
}