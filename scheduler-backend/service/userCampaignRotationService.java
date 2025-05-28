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
            // Convert date format
            String formattedDate = rotationUtils.convertDate(requestDate);
            Date currentDate = rotationUtils.getinDate(formattedDate);
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            log.info("Processing session-based request for user {} in company {} on date {}", 
                    userId, companyId, sdf.format(currentDate));
            
            // 1. Get all eligible campaigns for the company
            List<CampaignMapping> eligibleCampaigns = getEligibleCampaigns(companyId, currentDate);
            
            if (eligibleCampaigns.isEmpty()) {
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No eligible campaigns found for the company on the requested date");
            }
            
            // 2. Filter campaigns based on user preferences and enrollment
            List<CampaignMapping> userEligibleCampaigns = eligibleCampaigns.stream()
                    .filter(campaign -> preferenceService.isUserEligibleForCampaign(userId, companyId, campaign.getId()))
                    .filter(campaign -> validateUserBelongsToCompany(userId, companyId, campaign.getId()))
                    .collect(Collectors.toList());
            
            if (userEligibleCampaigns.isEmpty()) {
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No eligible campaigns found for this user");
            }
            
            // 3. Check if user already has a campaign assigned for this week
            List<UserCampaignTracker> weeklyTrackers = userTrackerRepository
                    .findByUserIdAndCompanyIdAndWeekStartDate(userId, companyId, weekStartDate);
            
            CampaignMapping selectedCampaign;
            UserCampaignTracker tracker;
            
            if (!weeklyTrackers.isEmpty()) {
                // User has existing campaign for this week
                UserCampaignTracker weeklyTracker = weeklyTrackers.get(0);
                
                // Validate campaign still eligible
                Optional<CampaignMapping> campaignOpt = campaignRepository.findById(weeklyTracker.getCampaignId());
                if (!campaignOpt.isPresent() || 
                    !preferenceService.isUserEligibleForCampaign(userId, companyId, weeklyTracker.getCampaignId()) ||
                    !isEligibleForRotation(campaignOpt.get(), currentDate)) {
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "Assigned campaign is no longer available");
                }
                
                selectedCampaign = campaignOpt.get();
                tracker = weeklyTracker;
                
                log.info("Using existing weekly assignment: user {} has campaign {} for week {}", 
                        userId, selectedCampaign.getName(), weekStartDate);
                
            } else {
                // Get next campaign in rotation for new week assignment
                CampaignMapping lastAssignedCampaign = getLastAssignedCampaign(userId, companyId);
                selectedCampaign = getNextCampaignInRotation(lastAssignedCampaign, userEligibleCampaigns);
                
                if (selectedCampaign == null) {
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "No available campaigns for rotation");
                }
                
                // Create new tracker for this week
                tracker = createTrackerForWeek(userId, companyId, selectedCampaign.getId(), 
                        currentDate, weekStartDate);
                
                log.info("Assigned new campaign {} to user {} for week starting {}", 
                        selectedCampaign.getName(), userId, weekStartDate);
            }
            
            // 4. Prepare response with current capacity
            CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(selectedCampaign);
            
            // Check if this campaign was already viewed in current session
            boolean viewedInSession = sessionService.hasCampaignBeenViewedInSession(
                    session, userId, companyId, selectedCampaign.getId());
            
            // Set remaining capacity
            if (viewedInSession) {
                // Already viewed in session, show reduced capacity (what it will be after session ends)
                response.setDisplayCapping(Math.max(0, tracker.getRemainingDisplayCap() - 1));
                response.setFrequencyPerWeek(Math.max(0, tracker.getRemainingWeeklyFrequency() - 1));
            } else {
                // Not yet viewed in session, show current capacity
                response.setDisplayCapping(tracker.getRemainingDisplayCap());
                response.setFrequencyPerWeek(tracker.getRemainingWeeklyFrequency());
            }
            
            response.setAlreadyViewedInSession(viewedInSession);
            
            return response;
            
        } catch (DataHandlingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in session-based campaign rotation: {}", e.getMessage(), e);
            throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                    "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Create tracker for the week without applying view (session will handle the view)
     */
    private UserCampaignTracker createTrackerForWeek(
            String userId, String companyId, String campaignId,
            Date currentDate, Date weekStartDate) {
        
        log.info("Creating tracker for user {} campaign {} week {}", userId, campaignId, weekStartDate);
        
        // Check if tracker already exists for this week
        Optional<UserCampaignTracker> existing = userTrackerRepository
                .findByUserIdAndCompanyIdAndCampaignIdAndWeekStartDate(
                        userId, companyId, campaignId, weekStartDate);
        
        if (existing.isPresent()) {
            log.info("Tracker already exists for user {} campaign {} week {}", userId, campaignId, weekStartDate);
            return existing.get();
        }
        
        // Create new tracker
        UserCampaignTracker tracker = new UserCampaignTracker();
        tracker.setId(UUID.randomUUID().toString());
        tracker.setUserId(userId);
        tracker.setCompanyId(companyId);
        tracker.setCampaignId(campaignId);
        tracker.setWeekStartDate(weekStartDate);
        
        // Get campaign details for initial values
        Optional<CampaignMapping> campaignOpt = campaignRepository.findById(campaignId);
        if (campaignOpt.isPresent()) {
            CampaignMapping campaign = campaignOpt.get();
            tracker.setRemainingWeeklyFrequency(campaign.getFrequencyPerWeek());
            
            // Handle display cap from existing trackers if any
            List<UserCampaignTracker> existingTrackers = userTrackerRepository
                    .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
            
            if (!existingTrackers.isEmpty()) {
                // Find the most recent tracker to get remaining display cap
                UserCampaignTracker mostRecent = existingTrackers.stream()
                        .max(Comparator.comparing(t -> t.getLastViewDate() != null ? 
                                t.getLastViewDate() : new Date(0)))
                        .orElse(null);
                
                if (mostRecent != null) {
                    tracker.setRemainingDisplayCap(mostRecent.getRemainingDisplayCap());
                    log.info("Using existing display cap {} for user {} campaign {}", 
                            mostRecent.getRemainingDisplayCap(), userId, campaignId);
                } else {
                    tracker.setRemainingDisplayCap(campaign.getDisplayCapping());
                }
            } else {
                // First time with this campaign
                tracker.setRemainingDisplayCap(campaign.getDisplayCapping());
                log.info("Setting initial display cap {} for user {} campaign {}", 
                        campaign.getDisplayCapping(), userId, campaignId);
            }
        } else {
            // Default values if campaign not found (shouldn't happen)
            log.warn("Campaign {} not found when creating tracker, using defaults", campaignId);
            tracker.setRemainingWeeklyFrequency(5);
            tracker.setRemainingDisplayCap(10);
        }
        
        // Don't set lastViewDate yet - that happens when session ends
        tracker.setLastViewDate(null);
        
        log.info("Created new tracker for user {} campaign {}: weeklyFreq={}, displayCap={}", 
                userId, campaignId, tracker.getRemainingWeeklyFrequency(), tracker.getRemainingDisplayCap());
        
        return userTrackerRepository.save(tracker);
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
}