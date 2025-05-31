// UserInsightClosureService.java
package com.usbank.corp.dcr.api.service;

import com.usbank.corp.dcr.api.entity.*;
import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.model.*;
import com.usbank.corp.dcr.api.repository.*;

import entity.UserCampaignTracker;
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

    private static final long ONE_MONTH_MILLIS = 30L * 24L * 60L * 60L * 1000L; // 30 days in milliseconds

    @Autowired
    private UserInsightClosureRepository closureRepository;

    @Autowired
    private UserGlobalPreferenceRepository globalPreferenceRepository;

    @Autowired
    private UserCampaignTrackerRepository userTrackerRepository;

    /**
     * Check if user is in 1-month wait period (should see NO campaigns)
     */
    public boolean isUserInWaitPeriod(String userId, String companyId) {
        return isUserInWaitPeriod(userId, companyId, new Date());
    }

    /**
     * Check if user is in 1-month wait period at specific date
     * During wait period, user should see NO campaigns at all
     */
    public boolean isUserInWaitPeriod(String userId, String companyId, Date checkDate) {
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId);

        for (UserInsightClosure closure : closures) {
            if (closure.getNextEligibleDate() != null &&
                    closure.getNextEligibleDate().after(checkDate)) {
                log.info("User {} in 1-month wait period until {} (checked at {})",
                        userId, closure.getNextEligibleDate(), checkDate);
                return true;
            }
        }

        return false;
    }

    /**
 * Get all permanently blocked campaigns for a user
 * These campaigns should NEVER be shown again, even after wait period
 */
public List<String> getPermanentlyBlockedCampaignIds(String userId, String companyId) {
    List<UserInsightClosure> closures = closureRepository
            .findByUserIdAndCompanyId(userId, companyId);
    
    return closures.stream()
            .filter(closure -> closure.getNextEligibleDate() != null) // Has permanent block marker
            .map(UserInsightClosure::getCampaignId)
            .collect(Collectors.toList());
}

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
     */
    @Transactional
    public InsightClosureResponseDTO recordInsightClosure(String userId, String companyId,
            String campaignId, Date effectiveDate) throws DataHandlingException {

        log.info("Recording insight closure for user: {}, company: {}, campaign: {} at date: {}",
                userId, companyId, campaignId, effectiveDate);

        // Check if user has global opt-out at the effective date
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
            // First closure - just hide until next eligibility
            log.info("First closure for campaign {} at date {}. Hiding until next eligibility.", campaignId,
                    effectiveDate);
            closure.setFirstClosureDate(effectiveDate);
            response.setAction("HIDDEN_UNTIL_NEXT_ELIGIBILITY");
            response.setMessage("This insight will be hidden until you're next eligible to see it.");

        } else if (closure.getClosureCount() == 2) {
            // Second closure - ask if they want to see it again
            log.info("Second closure for campaign {} at date {}. Prompting user preference.", campaignId,
                    effectiveDate);
            response.setAction("PROMPT_USER_PREFERENCE");
            response.setMessage("Would you like to see this insight again in the future?");
            response.setRequiresUserInput(true);

        } else {
            // Multiple closures - check if we should ask about global opt-out
            log.info("Multiple closures ({}) for campaign {} at date {}.", closure.getClosureCount(), campaignId,
                    effectiveDate);
            response.setAction("CONSIDER_GLOBAL_OPTOUT");
            response.setMessage(
                    "You've closed this insight multiple times. Would you like to stop seeing all insights?");
            response.setRequiresUserInput(true);
        }

        closure.setUpdatedDate(effectiveDate);
        closureRepository.save(closure);
        return response;
    }

    /**
     * Handle user's response to "see again?" prompt (backward compatible)
     */
    @Transactional
    public void handleSecondClosureResponse(String userId, String companyId, String campaignId,
            boolean wantsToSeeAgain, String reason) throws DataHandlingException {
        handleSecondClosureResponse(userId, companyId, campaignId, wantsToSeeAgain, reason, new Date());
    }

    /**
     * Handle user's response to "see again?" prompt with specific date
     */
    @Transactional
    public void handleSecondClosureResponse(String userId, String companyId, String campaignId,
            boolean wantsToSeeAgain, String reason, Date effectiveDate) throws DataHandlingException {

        UserInsightClosure closure = closureRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId)
                .orElseThrow(() -> new DataHandlingException(HttpStatus.NOT_FOUND.toString(),
                        "No closure record found"));

        if (wantsToSeeAgain) {
            // User wants to see it again - reset everything
            log.info("User wants to see campaign {} again at date {}. Resetting closure status.", campaignId,
                    effectiveDate);
            closure.setClosureCount(0); // CRITICAL FIX FOR ISSUE 2
            closure.setPermanentlyClosed(false);
            closure.setNextEligibleDate(null);
            closure.setClosureReason(null);
        } else {
            // User doesn't want to see it - set one month wait
            log.info("User doesn't want to see campaign {} again at date {}. Setting 1 month wait.", campaignId,
                    effectiveDate);
            closure.setClosureReason(reason);

            // CRITICAL: Use Calendar.add for proper month calculation
            Calendar cal = Calendar.getInstance();
            cal.setTime(effectiveDate);
            cal.add(Calendar.MONTH, 1);
            closure.setNextEligibleDate(cal.getTime());

            log.info("Next eligible date set to: {} for campaign {}", closure.getNextEligibleDate(), campaignId);

            // Check if this is part of a pattern - multiple campaigns closed
            checkForGlobalOptOutPattern(userId, companyId, effectiveDate);
        }

        closure.setUpdatedDate(effectiveDate);
        closureRepository.save(closure);
    }

    /**
     * Handle global opt-out request (backward compatible)
     */
    @Transactional
    public void handleGlobalOptOut(String userId, String reason) {
        handleGlobalOptOut(userId, reason, new Date());
    }

    /**
     * Handle global opt-out request with specific date
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
     * Re-enable insights for a user (opt back in) (backward compatible)
     */
    @Transactional
    public void enableInsights(String userId) {
        enableInsights(userId, new Date());
    }

    /**
     * Re-enable insights for a user with specific date
     */
    @Transactional
    public void enableInsights(String userId, Date effectiveDate) {
        log.info("Re-enabling insights for user: {} at date: {}", userId, effectiveDate);

        UserGlobalPreference preference = globalPreferenceRepository
                .findByUserId(userId)
                .orElse(createNewGlobalPreference(userId));

        preference.setInsightsEnabled(true);
        preference.setOptOutDate(null);
        preference.setOptOutReason(null);
        preference.setUpdatedDate(effectiveDate);

        globalPreferenceRepository.save(preference);

        // Update all closure records
        List<UserInsightClosure> userClosures = closureRepository.findByUserId(userId);
        for (UserInsightClosure closure : userClosures) {
            closure.setOptOutAllInsights(false);
            closure.setUpdatedDate(effectiveDate);
            closureRepository.save(closure);
        }
    }

    /**
     * Check if user should be prompted for global opt-out
     */
    private void checkForGlobalOptOutPattern(String userId, String companyId, Date effectiveDate) {
        // Count how many campaigns user has closed multiple times
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId);

        long multipleClosureCount = closures.stream()
                .filter(c -> c.getClosureCount() >= 2 && !c.getPermanentlyClosed())
                .count();

        if (multipleClosureCount >= 3) {
            log.info("User {} has closed {} campaigns multiple times at date {}. Consider global opt-out prompt.",
                    userId, multipleClosureCount, effectiveDate);
        }
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
        if (closure.getNextEligibleDate() != null) {
            return true; // NEVER show this campaign again
        }
        
        // If temporarily closed (closureCount >= 1)
        if (closure.getClosureCount() >= 1) {
            return true; // Closed until next rotation cycle
        }
        
        return false;
    }

    /**
     * Get all closed campaigns for a user (backward compatible)
     */
    public List<String> getClosedCampaignIds(String userId, String companyId) {
        return getClosedCampaignIds(userId, companyId, new Date());
    }

    /**
     * Get all closed campaigns for a user at specific date
     */
    public List<String> getClosedCampaignIds(String userId, String companyId, Date checkDate) {
        List<UserInsightClosure> closures = closureRepository
                .findByUserIdAndCompanyId(userId, companyId);
        
        List<String> temporarilyClosedCampaignIds = new ArrayList<>();
        
        for (UserInsightClosure closure : closures) {
            // Only include temporarily closed campaigns (closureCount >= 1 but no permanent block)
            if (closure.getClosureCount() >= 1 && closure.getNextEligibleDate() == null) {
                temporarilyClosedCampaignIds.add(closure.getCampaignId());
            }
        }
        
        log.debug("Found {} temporarily closed campaigns for user {} at date {}: {}", 
                 temporarilyClosedCampaignIds.size(), userId, checkDate, temporarilyClosedCampaignIds);
        
        return temporarilyClosedCampaignIds;
    }

    /**
     * Helper method to check closure based on date
     */
    private boolean isCampaignClosedBasedOnClosure(UserInsightClosure closure, Date checkDate) {
        // CRITICAL: If closure count is 0, not closed
        if (closure.getClosureCount() == 0) {
            return false;
        }

        // Check permanent closure
        if (closure.getPermanentlyClosed()) {
            return true;
        }

        // Check wait period
        if (closure.getNextEligibleDate() != null &&
                closure.getNextEligibleDate().after(checkDate)) {
            return true;
        }

        // For first closure, check if eligible after other campaigns
        if (closure.getClosureCount() == 1 && closure.getLastClosureDate() != null) {
            return !hasViewedOtherCampaignsSince(closure.getUserId(),
                    closure.getCompanyId(), closure.getCampaignId(),
                    closure.getLastClosureDate(), checkDate);
        }

        return false;
    }

    /**
     * Check if user has viewed other campaigns since a date (backward compatible)
     */
    private boolean hasViewedOtherCampaignsSince(String userId, String companyId,
            String excludeCampaignId, Date sinceDate) {
        return hasViewedOtherCampaignsSince(userId, companyId, excludeCampaignId, sinceDate, new Date());
    }

    /**
     * Check if user has viewed other campaigns since a date up to check date
     */
    private boolean hasViewedOtherCampaignsSince(String userId, String companyId,
            String excludeCampaignId, Date sinceDate, Date checkDate) {
        List<UserCampaignTracker> trackers = userTrackerRepository
                .findRecentByUserIdAndCompanyId(userId, companyId);

        return trackers.stream()
                .anyMatch(t -> !t.getCampaignId().equals(excludeCampaignId) &&
                        t.getLastViewDate() != null &&
                        t.getLastViewDate().after(sinceDate) &&
                        !t.getLastViewDate().after(checkDate));
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
        boolean hasGlobalOptOut = closureRepository.hasUserOptedOutGlobally(userId);
        if (hasGlobalOptOut) {
            log.debug("User {} has global opt-out flag in closure records", userId);
        }
        return hasGlobalOptOut;
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