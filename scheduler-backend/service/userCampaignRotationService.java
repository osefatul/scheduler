// Complete UserCampaignRotationService.java
package com.usbank.corp.dcr.api.service;

import com.usbank.corp.dcr.api.entity.CampaignMapping;
import com.usbank.corp.dcr.api.entity.UserCampaignTracker;
import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.model.CampaignResponseDTO;
import com.usbank.corp.dcr.api.model.EnrolledUserDTO;
import com.usbank.corp.dcr.api.model.UserDTO;
import com.usbank.corp.dcr.api.repository.CampaignRepository;
import com.usbank.corp.dcr.api.repository.UserCampaignTrackerRepository;
import com.usbank.corp.dcr.api.util.RotationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserCampaignRotationService {

    @Autowired
    RotationUtils rotationUtils;

    @Autowired
    private UserCampaignTrackerRepository userTrackerRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private RMManageCampaignService rmManageCampaignService;

    @Autowired
    private CampaignCompanyService campaignCompanyService;

    @Autowired
    private UserInsightPreferenceService preferenceService;

    @Autowired
    private UserSessionService sessionService;

    // Simple cache for user validation
    private Map<String, Map<String, Long>> validationCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 15 * 60 * 1000; // 15 minutes

    /**
     * Main method: Get the next eligible campaign for a user with session management.
     * Session is treated as one view regardless of page refreshes.
     */
    @Transactional
    public CampaignResponseDTO getNextEligibleCampaignWithSession(
            String requestDate, String companyId, String userId, HttpSession session) 
            throws DataHandlingException {
        
        try {
            String formattedDate = rotationUtils.convertDate(requestDate);
            Date currentDate = rotationUtils.getinDate(formattedDate);
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            log.info("=== PROCESSING REQUEST ===");
            log.info("User: {}, Company: {}, Date: {}, Session: {}", userId, companyId, currentDate, session.getId());
            log.info("Week start date: {}", weekStartDate);
            
            // 1. Get eligible campaigns
            List<CampaignMapping> eligibleCampaigns = getEligibleCampaigns(companyId, currentDate);
            if (eligibleCampaigns.isEmpty()) {
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No eligible campaigns found for the company on the requested date");
            }
            
            List<CampaignMapping> userEligibleCampaigns = eligibleCampaigns.stream()
                    .filter(campaign -> preferenceService.isUserEligibleForCampaign(userId, companyId, campaign.getId()))
                    .filter(campaign -> validateUserBelongsToCompany(userId, companyId, campaign.getId()))
                    .collect(Collectors.toList());
            
            if (userEligibleCampaigns.isEmpty()) {
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No eligible campaigns found for this user");
            }
            
            log.info("Found {} eligible campaigns for user", userEligibleCampaigns.size());
            
            // 2. Check for existing weekly tracker
            List<UserCampaignTracker> weeklyTrackers = userTrackerRepository
                    .findByUserIdAndCompanyIdAndWeekStartDate(userId, companyId, weekStartDate);
            
            CampaignMapping selectedCampaign;
            UserCampaignTracker tracker;
            
            if (!weeklyTrackers.isEmpty()) {
                log.info("Found existing weekly tracker for user {} week {}", userId, weekStartDate);
                
                UserCampaignTracker weeklyTracker = weeklyTrackers.get(0);
                log.info("Existing tracker - Campaign: {}, WeeklyFreq: {}, DisplayCap: {}", 
                        weeklyTracker.getCampaignId(), 
                        weeklyTracker.getRemainingWeeklyFrequency(), 
                        weeklyTracker.getRemainingDisplayCap());
                
                Optional<CampaignMapping> campaignOpt = campaignRepository.findById(weeklyTracker.getCampaignId());
                if (!campaignOpt.isPresent() || 
                    !preferenceService.isUserEligibleForCampaign(userId, companyId, weeklyTracker.getCampaignId()) ||
                    !isEligibleForRotation(campaignOpt.get(), currentDate)) {
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "Assigned campaign is no longer available");
                }
                
                selectedCampaign = campaignOpt.get();
                tracker = weeklyTracker;
                
            } else {
                log.info("No existing weekly tracker - creating new assignment for user {} week {}", userId, weekStartDate);
                
                // Get next campaign in rotation
                CampaignMapping lastAssignedCampaign = getLastAssignedCampaign(userId, companyId);
                selectedCampaign = getNextCampaignInRotation(lastAssignedCampaign, userEligibleCampaigns);
                
                if (selectedCampaign == null) {
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "No available campaigns for rotation");
                }
                
                log.info("Selected campaign for new week: {} ({})", selectedCampaign.getName(), selectedCampaign.getId());
                
                // Create new tracker with proper initial values
                tracker = createTrackerForWeek(userId, companyId, selectedCampaign, weekStartDate);
                
                log.info("Created new tracker - WeeklyFreq: {}, DisplayCap: {}", 
                        tracker.getRemainingWeeklyFrequency(), tracker.getRemainingDisplayCap());
            }
            
            // 3. Handle session tracking and DB updates
            boolean alreadyViewedInSession = sessionService.hasCampaignBeenViewedInSession(
                    session, userId, companyId, selectedCampaign.getId());
            
            log.info("Campaign {} already viewed in session {}: {}", selectedCampaign.getId(), session.getId(), alreadyViewedInSession);
            
            if (!alreadyViewedInSession) {
                log.info("First view in session - applying DB update");
                
                // Check if tracker has remaining capacity
                if (tracker.getRemainingWeeklyFrequency() <= 0) {
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "No more weekly views available for this campaign");
                }
                
                if (tracker.getRemainingDisplayCap() <= 0) {
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "Campaign has reached its display cap limit");
                }
                
                // Apply view to session and DB
                boolean isNewView = sessionService.markCampaignViewedInSession(
                        session, userId, companyId, selectedCampaign.getId());
                
                if (isNewView) {
                    log.info("Applied view to DB for user {} campaign {}", userId, selectedCampaign.getId());
                    
                    // Refresh tracker to get updated values
                    Optional<UserCampaignTracker> updatedTrackerOpt = userTrackerRepository
                            .findByUserIdAndCompanyIdAndCampaignIdAndWeekStartDate(
                                    userId, companyId, selectedCampaign.getId(), weekStartDate);
                    
                    if (updatedTrackerOpt.isPresent()) {
                        tracker = updatedTrackerOpt.get();
                        log.info("Refreshed tracker after DB update - WeeklyFreq: {}, DisplayCap: {}", 
                                tracker.getRemainingWeeklyFrequency(), tracker.getRemainingDisplayCap());
                    }
                }
            } else {
                log.info("Already viewed in session - returning current tracker values");
            }
            
            // 4. Prepare response
            CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(selectedCampaign);
            response.setDisplayCapping(tracker.getRemainingDisplayCap());
            response.setFrequencyPerWeek(tracker.getRemainingWeeklyFrequency());
            response.setAlreadyViewedInSession(alreadyViewedInSession);
            response.setSessionId(session.getId());
            
            log.info("=== FINAL RESPONSE ===");
            log.info("Campaign: {} ({})", response.getName(), response.getId());
            log.info("DisplayCapping: {}", response.getDisplayCapping());
            log.info("FrequencyPerWeek: {}", response.getFrequencyPerWeek());
            log.info("AlreadyViewedInSession: {}", response.getAlreadyViewedInSession());
            
            return response;
            
        } catch (DataHandlingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                    "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Create tracker for the week without applying view (session will handle the view)
     */
    private UserCampaignTracker createTrackerForWeek(
        String userId, String companyId, CampaignMapping campaign, Date weekStartDate) {
    
    log.info("Creating new tracker for user {} campaign {} week {}", userId, campaign.getId(), weekStartDate);
    
    // Double-check that tracker doesn't already exist
    Optional<UserCampaignTracker> existing = userTrackerRepository
            .findByUserIdAndCompanyIdAndCampaignIdAndWeekStartDate(
                    userId, companyId, campaign.getId(), weekStartDate);
    
    if (existing.isPresent()) {
        log.info("Tracker already exists - returning existing one");
        return existing.get();
    }
    
    // Create new tracker
    UserCampaignTracker tracker = new UserCampaignTracker();
    tracker.setId(UUID.randomUUID().toString());
    tracker.setUserId(userId);
    tracker.setCompanyId(companyId);
    tracker.setCampaignId(campaign.getId());
    tracker.setWeekStartDate(weekStartDate);
    
    // Set initial weekly frequency from campaign
    Integer campaignWeeklyFreq = campaign.getFrequencyPerWeek();
    if (campaignWeeklyFreq == null || campaignWeeklyFreq <= 0) {
        campaignWeeklyFreq = 5; // Default
        log.warn("Campaign {} has null/zero weekly frequency, using default: {}", campaign.getId(), campaignWeeklyFreq);
    }
    tracker.setRemainingWeeklyFrequency(campaignWeeklyFreq);
    
    // Set display cap - check for existing usage across all weeks
    Integer campaignDisplayCap = campaign.getDisplayCapping();
    if (campaignDisplayCap == null || campaignDisplayCap <= 0) {
        campaignDisplayCap = 10; // Default
        log.warn("Campaign {} has null/zero display capping, using default: {}", campaign.getId(), campaignDisplayCap);
    }
    
    // Check if user has previous trackers for this campaign (any week)
    List<UserCampaignTracker> allUserTrackers = userTrackerRepository
            .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaign.getId());
    
    if (!allUserTrackers.isEmpty()) {
        // Find the most recent tracker to get remaining display cap
        UserCampaignTracker mostRecent = allUserTrackers.stream()
                .max(Comparator.comparing(t -> t.getLastViewDate() != null ? 
                        t.getLastViewDate() : new Date(0)))
                .orElse(null);
        
        if (mostRecent != null && mostRecent.getRemainingDisplayCap() != null) {
            tracker.setRemainingDisplayCap(mostRecent.getRemainingDisplayCap());
            log.info("Using remaining display cap from previous tracker: {}", mostRecent.getRemainingDisplayCap());
        } else {
            tracker.setRemainingDisplayCap(campaignDisplayCap);
            log.info("No valid previous display cap found, using campaign default: {}", campaignDisplayCap);
        }
    } else {
        // First time with this campaign
        tracker.setRemainingDisplayCap(campaignDisplayCap);
        log.info("First time with campaign, using campaign display cap: {}", campaignDisplayCap);
    }
    
    // No last view date yet
    tracker.setLastViewDate(null);
    
    log.info("Saving new tracker - WeeklyFreq: {}, DisplayCap: {}", 
            tracker.getRemainingWeeklyFrequency(), tracker.getRemainingDisplayCap());
    
    UserCampaignTracker savedTracker = userTrackerRepository.save(tracker);
    
    log.info("Successfully created tracker with ID: {}", savedTracker.getId());
    
    return savedTracker;
}
    
    /**
     * Get the last campaign assigned to a user from any previous week
     */
    private CampaignMapping getLastAssignedCampaign(String userId, String companyId) {
        try {
            log.info("Getting last assigned campaign for user {} company {}", userId, companyId);
            
            List<UserCampaignTracker> allTrackers = userTrackerRepository
                    .findByUserIdAndCompanyId(userId, companyId);
            
            if (allTrackers.isEmpty()) {
                log.info("No previous campaign assignments for user {} company {}", userId, companyId);
                return null;
            }
            
            // Find the most recent tracker based on week start date
            UserCampaignTracker mostRecentTracker = allTrackers.stream()
                    .max(Comparator.comparing(UserCampaignTracker::getWeekStartDate))
                    .orElse(null);
            
            if (mostRecentTracker == null) {
                log.info("No recent tracker found for user {} company {}", userId, companyId);
                return null;
            }
            
            // Get the campaign details
            Optional<CampaignMapping> campaignOpt = campaignRepository
                    .findById(mostRecentTracker.getCampaignId());
            
            if (campaignOpt.isPresent()) {
                log.info("Last assigned campaign for user {} was {} in week {}", 
                        userId, campaignOpt.get().getName(), mostRecentTracker.getWeekStartDate());
                return campaignOpt.get();
            } else {
                log.warn("Last assigned campaign {} not found for user {}", 
                        mostRecentTracker.getCampaignId(), userId);
                return null;
            }
        } catch (Exception e) {
            log.error("Error getting last assigned campaign for user {} company {}: {}", 
                    userId, companyId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get the next campaign in rotation
     */
    private CampaignMapping getNextCampaignInRotation(
            CampaignMapping lastCampaign, 
            List<CampaignMapping> eligibleCampaigns) {
        
        if (eligibleCampaigns.isEmpty()) {
            log.warn("No eligible campaigns provided for rotation");
            return null;
        }
        
        // If this is the first campaign for the user, return the first eligible one
        if (lastCampaign == null) {
            log.info("No previous campaign, selecting first eligible: {}", eligibleCampaigns.get(0).getName());
            return eligibleCampaigns.get(0);
        }
        
        // Find last campaign's position in eligible campaigns list
        int lastIndex = -1;
        for (int i = 0; i < eligibleCampaigns.size(); i++) {
            if (eligibleCampaigns.get(i).getId().equals(lastCampaign.getId())) {
                lastIndex = i;
                break;
            }
        }
        
        // If last campaign not found in current eligible list, start from beginning
        if (lastIndex == -1) {
            log.info("Last campaign {} not in current eligible list, starting from first: {}", 
                    lastCampaign.getName(), eligibleCampaigns.get(0).getName());
            return eligibleCampaigns.get(0);
        }
        
        // Get the next campaign in rotation (circular)
        int nextIndex = (lastIndex + 1) % eligibleCampaigns.size();
        CampaignMapping nextCampaign = eligibleCampaigns.get(nextIndex);
        
        log.info("Rotating from {} to {} (index {} to {})", 
                lastCampaign.getName(), nextCampaign.getName(), lastIndex, nextIndex);
        
        return nextCampaign;
    }
    
    /**
     * Validate that a user belongs to a company for a specific campaign
     * Uses caching to reduce external API calls
     */
    private boolean validateUserBelongsToCompany(String userId, String companyId, String campaignId) {
        String userCompanyKey = userId + ":" + companyId;
        
        log.debug("Validating user-company relationship for campaign {}: userId={}, companyId={}", 
                campaignId, userId, companyId);
        
        long currentTime = System.currentTimeMillis();
        try {
            // Check cache first
            Map<String, Long> campaignCache = validationCache.get(campaignId);
            if (campaignCache != null) {
                Long timestamp = campaignCache.get(userCompanyKey);
                if (timestamp != null && (currentTime - timestamp) < CACHE_EXPIRY_MS) {
                    log.debug("Cache hit for user {} company {} campaign {}", userId, companyId, campaignId);
                    return true;
                }
            }
            
            log.debug("Cache miss or expired, fetching enrolled users for campaign {}", campaignId);
            
            // Cache miss or expired, check with service
            EnrolledUserDTO enrolledUsersDTO = rmManageCampaignService.getEnrolledUsers(campaignId);
            
            boolean userCompanyFound = false;
            if (enrolledUsersDTO != null && enrolledUsersDTO.getUsersList() != null) {
                for (UserDTO user : enrolledUsersDTO.getUsersList()) {
                    if (user.getUserName().equals(userId) && user.getCompanyName().equals(companyId)) {
                        userCompanyFound = true;
                        log.debug("Found user {} in company {} for campaign {}", userId, companyId, campaignId);
                        break;
                    }
                }
            } else {
                log.warn("No enrolled users data for campaign {}", campaignId);
            }
            
            if (userCompanyFound) {
                // Update cache
                Map<String, Long> userCompanyCache = validationCache.computeIfAbsent(
                        campaignId, k -> new ConcurrentHashMap<>());
                userCompanyCache.put(userCompanyKey, currentTime);
                log.debug("Updated cache for user {} company {} campaign {}", userId, companyId, campaignId);
            } else {
                log.info("User {} not enrolled in campaign {} for company {}", userId, campaignId, companyId);
            }
            
            return userCompanyFound;
        } catch (Exception e) {
            log.error("Error validating user-company relationship for campaign {}: {}", 
                    campaignId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get all eligible campaigns for a company
     */
    private List<CampaignMapping> getEligibleCampaigns(String companyId, Date currentDate) {
        log.info("Getting eligible campaigns for company {} on date {}", companyId, currentDate);
        
        // Get all campaign IDs associated with this company
        List<String> campaignIds = campaignCompanyService.getCampaignsForCompany(companyId);
        
        log.info("Found {} campaign associations for company {}", campaignIds.size(), companyId);
        
        if (campaignIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Get campaign details
        List<CampaignMapping> campaigns = campaignRepository.findAllById(campaignIds);
        
        log.info("Retrieved {} campaign details for company {}", campaigns.size(), companyId);
        
        // Filter campaigns based on eligibility criteria
        List<CampaignMapping> eligibleCampaigns = campaigns.stream()
                .filter(campaign -> isEligibleForRotation(campaign, currentDate))
                .collect(Collectors.toList());
        
        log.info("Found {} eligible campaigns for company {} after filtering", 
                eligibleCampaigns.size(), companyId);
        
        return eligibleCampaigns;
    }
    
    /**
     * Check if a campaign is eligible for rotation
     */
    private boolean isEligibleForRotation(CampaignMapping campaign, Date currentDate) {
        // Check if campaign status is active
        String status = campaign.getStatus();
        if (!"ACTIVE".equals(status) && !"INPROGRESS".equals(status) && !"SCHEDULED".equals(status)) {
            log.debug("Campaign {} not eligible: status is {}", campaign.getId(), status);
            return false;
        }
        
        // Check if campaign is within date range
        if (!isWithinDateRange(campaign, currentDate)) {
            log.debug("Campaign {} not eligible: outside date range", campaign.getId());
            return false;
        }
        
        // Check if campaign is completed
        if ("COMPLETED".equals(campaign.getVisibility())) {
            log.debug("Campaign {} not eligible: marked as completed", campaign.getId());
            return false;
        }
        
        log.debug("Campaign {} is eligible for rotation", campaign.getId());
        return true;
    }
    
    /**
     * Check if current date is within campaign's date range
     */
    private boolean isWithinDateRange(CampaignMapping campaign, Date date) {
        Date startDate = campaign.getStartDate();
        Date endDate = campaign.getEndDate();
        
        // If start date is null, assume no start constraint
        boolean afterStart = startDate == null || !date.before(startDate);
        
        // If end date is null, assume no end constraint
        boolean beforeEnd = endDate == null || !date.after(endDate);
        
        boolean withinRange = afterStart && beforeEnd;
        
        log.debug("Campaign {} date range check: start={}, end={}, current={}, withinRange={}", 
                campaign.getId(), startDate, endDate, date, withinRange);
        
        return withinRange;
    }
    
    /**
     * Legacy method: Get next eligible campaign without session management
     * Kept for backward compatibility - delegates to session-based method
     */
    @Transactional
    public CampaignResponseDTO getNextEligibleCampaignForUser(String requestDate, String companyId, String userId) 
            throws DataHandlingException {
        
        log.warn("Using legacy method getNextEligibleCampaignForUser - consider upgrading to session-based method");
        
        // For backward compatibility, we'll create a mock session or handle without session
        // In practice, you should use the session-based method
        throw new DataHandlingException(HttpStatus.BAD_REQUEST.toString(),
                "Legacy method no longer supported. Please use session-based campaign rotation.");
    }



        /**
     * DEBUG METHOD: Get current tracker state
     */
    public String getTrackerDebugInfo(String userId, String companyId, String campaignId) {
        try {
            Date currentDate = new Date();
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            Optional<UserCampaignTracker> trackerOpt = userTrackerRepository
                    .findByUserIdAndCompanyIdAndCampaignIdAndWeekStartDate(
                            userId, companyId, campaignId, weekStartDate);
            
            if (trackerOpt.isPresent()) {
                UserCampaignTracker tracker = trackerOpt.get();
                return String.format(
                    "Tracker found - WeeklyFreq: %d, DisplayCap: %d, LastView: %s",
                    tracker.getRemainingWeeklyFrequency(),
                    tracker.getRemainingDisplayCap(),
                    tracker.getLastViewDate()
                );
            } else {
                return "No tracker found for this user/campaign/week";
            }
        } catch (Exception e) {
            return "Error getting tracker info: " + e.getMessage();
        }
    }
}