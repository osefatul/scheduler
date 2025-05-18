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
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    // Simple two-level cache implementation
    // Map of campaignId -> Map of (userId:companyId -> timestamp)
    private Map<String, Map<String, Long>> validationCache = new ConcurrentHashMap<>();

    // Cache expiry time in milliseconds (15 minutes)
    private static final long CACHE_EXPIRY_MS = 15 * 60 * 1000;

    /**
     * Get the next eligible campaign for a user based on rotation rules.
     * This version enforces one campaign per week rule.
     */
    @Transactional
    public CampaignResponseDTO getNextEligibleCampaignForUser(String requestDate, String companyId, String userId) 
            throws DataHandlingException {
        try {
            // Convert date format
            String formattedDate = rotationUtils.convertDate(requestDate);
            Date currentDate = rotationUtils.getinDate(formattedDate);
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            log.info(String.format("Processing request for user {} in company {} on date {}", 
                    userId, companyId, sdf.format(currentDate)));
            
            // Get all eligible campaigns for the company
            List<CampaignMapping> eligibleCampaigns = getEligibleCampaigns(companyId, currentDate);
            
            if (eligibleCampaigns.isEmpty()) {
                log.info(String.format("No eligible campaigns found for company {} on date {}", 
                        companyId, sdf.format(currentDate)));
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No eligible campaigns found for the company on the requested date");
            }
            
            // Validate that the user belongs to the company for at least one of the eligible campaigns
            boolean validatedForAnyCampaign = false;
            for (CampaignMapping campaign : eligibleCampaigns) {
                if (validateUserBelongsToCompany(userId, companyId, campaign.getId())) {
                    validatedForAnyCampaign = true;
                    break;
                }
            }
            
            if (!validatedForAnyCampaign) {
                log.warn(String.format("User {} is not enrolled in any eligible campaigns for company {}", userId, companyId));
                throw new DataHandlingException(HttpStatus.FORBIDDEN.toString(),
                        "User is not enrolled in any eligible campaigns for this company");
            }
            
            // Check if the user already has a campaign assigned for this week
            List<UserCampaignTracker> weeklyTrackers = userTrackerRepository
                    .findByUserIdAndCompanyIdAndWeekStartDate(userId, companyId, weekStartDate);
            
            if (!weeklyTrackers.isEmpty()) {
                // User already has a campaign assigned for this week
                
                // Find the campaign assigned for this week (should be only one due to one-campaign-per-week rule)
                UserCampaignTracker weeklyTracker = weeklyTrackers.get(0);
                
                // Check if this campaign has remaining weekly frequency
                if (weeklyTracker.getRemainingWeeklyFrequency() <= 0) {
                    // Weekly frequency exhausted, no more views for this week
                    log.info(String.format("User %s has exhausted weekly frequency for campaign %s", 
                            userId, weeklyTracker.getCampaignId()));
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "No more views available for this week");
                }
                
                // Check if this campaign has remaining display cap
                if (weeklyTracker.getRemainingDisplayCap() <= 0) {
                    // Display cap exhausted, no more views of this campaign ever
                    log.info(String.format("User %s has exhausted display cap for campaign %s", 
                            userId, weeklyTracker.getCampaignId()));
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "This campaign has reached its display cap limit");
                }
                
                // Get the campaign details
                Optional<CampaignMapping> campaignOpt = campaignRepository.findById(weeklyTracker.getCampaignId());
                if (!campaignOpt.isPresent()) {
                    throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Campaign not found for tracker");
                }
                
                CampaignMapping campaign = campaignOpt.get();
                
                // Verify user is still enrolled in this campaign
                if (!validateUserBelongsToCompany(userId, companyId, campaign.getId())) {
                    log.warn(String.format("User %s is no longer enrolled in campaign %s", userId, campaign.getId()));
                    // Since we enforce one campaign per week, and the user is no longer enrolled in their 
                    // assigned campaign, they can't see any campaigns this week
                    throw new DataHandlingException(HttpStatus.FORBIDDEN.toString(),
                            "User is no longer enrolled in the assigned campaign for this week");
                }
                
                // Verify campaign is still eligible
                if (!isEligibleForRotation(campaign, currentDate)) {
                    log.warn(String.format("Campaign %s is no longer eligible for rotation", campaign.getId()));
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "Assigned campaign is no longer eligible");
                }
                
                // Apply the view
                UserCampaignTracker updatedTracker = applyUserView(
                        userId, companyId, campaign.getId(), currentDate, weekStartDate);
                
                // Prepare response
                CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(campaign);
                response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
                response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());
                
                return response;
            }
            
            // If we get here, the user doesn't have a campaign assigned for this week yet
            // We need to select the next campaign in rotation
            
            // Find all campaigns the user is enrolled in
            List<CampaignMapping> enrolledCampaigns = eligibleCampaigns.stream()
                    .filter(campaign -> validateUserBelongsToCompany(userId, companyId, campaign.getId()))
                    .collect(Collectors.toList());
            
            if (enrolledCampaigns.isEmpty()) {
                log.info(String.format("No enrolled campaigns found for user %s in company %s", userId, companyId));
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No enrolled campaigns found for this user");
            }
            
            // Get the last campaign assigned to this user (from any previous week)
            CampaignMapping lastAssignedCampaign = getLastAssignedCampaign(userId, companyId);
            
            // Get the next campaign in rotation
            CampaignMapping nextCampaign = getNextCampaignInRotation(lastAssignedCampaign, enrolledCampaigns);
            
            if (nextCampaign == null) {
                log.info(String.format("No next campaign available for user %s in company %s", userId, companyId));
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No available campaigns found for this user");
            }
            
            log.info(String.format("Assigning new campaign to user %s for this week: %s (%s)", 
                    userId, nextCampaign.getName(), nextCampaign.getId()));
            
            // Apply the view to the new campaign
            UserCampaignTracker updatedTracker = applyUserView(
                    userId, companyId, nextCampaign.getId(), currentDate, weekStartDate);
            
            // Prepare response
            CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(nextCampaign);
            response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
            response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());
            
            return response;
            
        } catch (DataHandlingException e) {
            throw e;
        } catch (Exception e) {
            log.error(String.format("Unexpected error: %s", e.getMessage()), e);
            throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                    "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Get the last campaign assigned to a user from any previous week
     */
    private CampaignMapping getLastAssignedCampaign(String userId, String companyId) {
        try {
            // Find all trackers for this user and company
            List<UserCampaignTracker> allTrackers = userTrackerRepository
                    .findByUserIdAndCompanyId(userId, companyId);
            
            if (allTrackers.isEmpty()) {
                return null;
            }
            
            // Find the most recent tracker based on week start date
            UserCampaignTracker mostRecentTracker = allTrackers.stream()
                    .max(Comparator.comparing(UserCampaignTracker::getWeekStartDate))
                    .orElse(null);
            
            if (mostRecentTracker == null) {
                return null;
            }
            
            // Get the campaign details
            Optional<CampaignMapping> campaignOpt = campaignRepository
                    .findById(mostRecentTracker.getCampaignId());
            
            return campaignOpt.orElse(null);
        } catch (Exception e) {
            log.error(String.format("Error getting last assigned campaign: %s", e.getMessage()), e);
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
            return null;
        }
        
        // If this is the first campaign for the user, return the first eligible one
        if (lastCampaign == null) {
            return eligibleCampaigns.get(0);
        }
        
        // Find last campaign's position in eligible campaigns
        int lastIndex = -1;
        for (int i = 0; i < eligibleCampaigns.size(); i++) {
            if (eligibleCampaigns.get(i).getId().equals(lastCampaign.getId())) {
                lastIndex = i;
                break;
            }
        }
        
        // If not found (campaign no longer eligible), start from beginning
        if (lastIndex == -1) {
            return eligibleCampaigns.get(0);
        }
        
        // Get the next campaign in rotation
        int nextIndex = (lastIndex + 1) % eligibleCampaigns.size();
        return eligibleCampaigns.get(nextIndex);
    }
    
    /**
     * Validate that a user belongs to a company for a specific campaign
     * Returns true if validation passes, false otherwise
     */
    private boolean validateUserBelongsToCompany(String userId, String companyId, String campaignId) {
        String userCompanyKey = userId + ":" + companyId;
        
        // Give us log of this
        log.info(String.format("Validating user-company relationship for campaign %s: userId=%s, companyId=%s", 
                campaignId, userId, companyId));
        
        long currentTime = System.currentTimeMillis();
        try {
            // Check cache first
            Map<String, Long> campaignCache = validationCache.get(campaignId);
            if (campaignCache != null) {
                Long timestamp = campaignCache.get(userCompanyKey);
                if (timestamp != null && (currentTime - timestamp) < CACHE_EXPIRY_MS) {
                    // Cache hit with valid timestamp
                    return true;
                }
            }
            
            log.info(String.format("Fetching enrolled users for campaign ID: %s", campaignId));
            // Cache miss or expired, check with service
            EnrolledUserDTO enrolledUsersDTO = rmManageCampaignService.getEnrolledUsers(campaignId);
            log.info(String.format("Checking if user-company pair exists in enrolled users DTO: %s", enrolledUsersDTO));
            // Check if the user-company pair exists in the enrolled users
            // We need to check the usersList field in enrolledUsersDTO
            boolean userCompanyFound = false;
            if (enrolledUsersDTO != null && enrolledUsersDTO.getUsersList() != null) {
                log.info(String.format("Enrolled users list is not null. Iterating through the list."));
                // Use the usersList field to find matching user-company pair
                for (UserDTO user : enrolledUsersDTO.getUsersList()) {
                    log.debug(String.format("Checking user: userName=%s, companyName=%s", user.getUserName(), user.getCompanyName()));
                    if (user.getUserName().equals(userId) && user.getCompanyName().equals(companyId)) {
                        log.info(String.format("User-company pair found: userId=%s, companyId=%s", userId, companyId));
                        userCompanyFound = true;
                        break;
                    }
                }
            } else {
                log.warn(String.format("Enrolled users list is null or empty for campaign ID: %s", campaignId));
            }
            
            if (userCompanyFound) {
                log.info(String.format("Updating cache for campaign ID: %s with user-company pair: %s", campaignId, userCompanyKey));
                // Get or create campaign cache
                Map<String, Long> userCompanyCache = validationCache.computeIfAbsent(
                        campaignId, k -> new ConcurrentHashMap<>());
                
                // Store with current timestamp
                userCompanyCache.put(userCompanyKey, currentTime);
                
                log.info(String.format("Cache updated successfully for campaign ID: %s", campaignId));
            } else {
                log.warn(String.format("User-company pair not found for userId=%s and companyId=%s in campaign ID: %s", userId, 
                        companyId, campaignId));
            }
            
            log.info(String.format("Returning validation result: %s", userCompanyFound));
            return userCompanyFound;
        } catch (Exception e) {
            log.error(String.format("Error validating user-company relationship for campaign %s: %s", 
                    campaignId, e.getMessage()), e);
            // In case of error, we err on the side of caution and return false
            return false;
        }
    }
    
    /**
     * Refresh user-company validation cache for specific campaigns periodically
     */
    @Scheduled(fixedRate = 900000) // 15 minutes in milliseconds
    public void refreshValidationCache() {
        try {
            log.info(String.format("Refreshing user-company validation cache"));
            
            // Get active campaigns
            List<CampaignMapping> activeCampaigns = campaignRepository.findByStatus("ACTIVE");
            long currentTime = System.currentTimeMillis();
            
            for (CampaignMapping campaign : activeCampaigns) {
                try {
                    String campaignId = campaign.getId();
                    EnrolledUserDTO enrolledUsersDTO = rmManageCampaignService.getEnrolledUsers(campaignId);
                    
                    // Create a new cache map for this campaign
                    Map<String, Long> newCampaignCache = new ConcurrentHashMap<>();
                    
                    // Populate with all valid user-company pairs
                    if (enrolledUsersDTO != null && enrolledUsersDTO.getUsersList() != null) {
                        for (UserDTO user : enrolledUsersDTO.getUsersList()) {
                            String userCompanyKey = user.getUserName() + ":" + user.getCompanyName();
                            newCampaignCache.put(userCompanyKey, currentTime);
                        }
                    }
                    
                    // Replace existing cache for this campaign
                    validationCache.put(campaignId, newCampaignCache);
                    
                    log.info(String.format("Refreshed cache for campaign %s with %d enrolled users", 
                            campaignId, 
                            enrolledUsersDTO != null && enrolledUsersDTO.getUsersList() != null ? 
                                    enrolledUsersDTO.getUsersList().size() : 0));
                } catch (Exception e) {
                    log.error(String.format("Error refreshing cache for campaign %s: %s", 
                            campaign.getId(), e.getMessage()), e);
                }
            }
            
            // Clean up expired entries from the cache
            cleanupExpiredCacheEntries();
            
            log.info(String.format("Successfully refreshed user-company validation cache"));
        } catch (Exception e) {
            log.error(String.format("Error refreshing user-company cache: %s", e.getMessage()), e);
        }
    }
    
    private void cleanupExpiredCacheEntries() {
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<String, Map<String, Long>> campaignEntry : validationCache.entrySet()) {
            Map<String, Long> userCompanyMap = campaignEntry.getValue();
            
            // Remove expired entries
            Iterator<Map.Entry<String, Long>> iterator = userCompanyMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if ((currentTime - entry.getValue()) >= CACHE_EXPIRY_MS) {
                    iterator.remove();
                }
            }
            
            // If all entries were removed, remove the campaign from the cache
            if (userCompanyMap.isEmpty()) {
                validationCache.remove(campaignEntry.getKey());
            }
        }
    }
    
    /**
     * Get all eligible campaigns for a company
     */
    private List<CampaignMapping> getEligibleCampaigns(String companyId, Date currentDate) {
        // Get all campaigns for the company
        List<String> campaignIds = campaignCompanyService.getCampaignsForCompany(companyId);
        
        // add log for campaignIds
        log.info(String.format("Campaign IDs for company %s: %s", companyId, campaignIds));
        
        if (campaignIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Get campaign details
        List<CampaignMapping> campaigns = campaignRepository.findAllById(campaignIds);
        
        log.info(String.format("Campaigns for company %s: Total campaigns: %d", companyId, campaigns.size()));
        for (CampaignMapping campaign : campaigns) {
            log.info(String.format("Campaign ID: %s, Name: %s, Status: %s, Start Date: %s, End Date: %s", 
                    campaign.getId(), 
                    campaign.getName(),
                    campaign.getStatus(),
                    campaign.getStartDate(),
                    campaign.getEndDate()));
        }
        
        // Filter campaigns based on date range and other criteria
        return campaigns.stream()
                .filter(campaign -> isEligibleForRotation(campaign, currentDate))
                .collect(Collectors.toList());
    }
    
    /**
     * Check if a campaign is eligible for rotation
     */
    private boolean isEligibleForRotation(CampaignMapping campaign, Date currentDate) {
        // Check if campaign is active
        if (!"In progress".equals(campaign.getStatus())) {
            return false;
        }
        
        // Check date range
        return isWithinDateRange(campaign, currentDate);
    }
    
    private boolean isWithinDateRange(CampaignMapping campaign, Date date) {
        Date startDate = campaign.getStartDate();
        Date endDate = campaign.getEndDate();
        
        // If start date is null, assume no start constraint
        boolean afterStart = startDate == null || !date.before(startDate);
        
        // If end date is null, assume no end constraint
        boolean beforeEnd = endDate == null || !date.after(endDate);
        
        return afterStart && beforeEnd;
    }
    
    /**
     * Apply a view for a user and return the updated tracker
     */
    private UserCampaignTracker applyUserView(
        String userId, String companyId, String campaignId,
        Date currentDate, Date weekStartDate) {
    
        // First, check if there's an existing tracker for this user-campaign combination
        // regardless of the week
        List<UserCampaignTracker> existingTrackers = userTrackerRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
        
        // Get the specific tracker for this week if it exists
        UserCampaignTracker weeklyTracker = userTrackerRepository
                .findByUserIdAndCompanyIdAndCampaignIdAndWeekStartDate(
                        userId, companyId, campaignId, weekStartDate)
                .orElse(null);
        
        // If no tracker exists for this week, create a new one
        if (weeklyTracker == null) {
            Optional<CampaignMapping> campaignOpt = campaignRepository.findById(campaignId);
            if (!campaignOpt.isPresent()) {
                throw new RuntimeException("Campaign not found: " + campaignId);
            }
            
            CampaignMapping campaign = campaignOpt.get();
            
            // Create new tracker
            weeklyTracker = new UserCampaignTracker();
            weeklyTracker.setId(UUID.randomUUID().toString());
            weeklyTracker.setUserId(userId);
            weeklyTracker.setCompanyId(companyId);
            weeklyTracker.setCampaignId(campaignId);
            weeklyTracker.setWeekStartDate(weekStartDate);
            
            // Set weekly frequency from campaign
            weeklyTracker.setRemainingWeeklyFrequency(campaign.getFrequencyPerWeek());
            
            // For display cap, check if there's an existing tracker for this campaign
            if (!existingTrackers.isEmpty()) {
                // If there's an existing tracker, use its remaining display cap
                // Find the one with the most recent view date
                UserCampaignTracker mostRecentTracker = existingTrackers.stream()
                        .max(Comparator.comparing(
                                tracker -> tracker.getLastViewDate() != null ? 
                                        tracker.getLastViewDate() : new Date(0)))
                        .orElse(null);
                
                if (mostRecentTracker != null) {
                    // Use the remaining display cap from the most recent tracker
                    weeklyTracker.setRemainingDisplayCap(mostRecentTracker.getRemainingDisplayCap());
                    log.info(String.format("Using existing display cap for user %s, campaign %s: %d", 
                            userId, campaignId, weeklyTracker.getRemainingDisplayCap()));
                } else {
                    // This shouldn't happen, but set to campaign default if it does
                    weeklyTracker.setRemainingDisplayCap(campaign.getDisplayCapping());
                    log.info(String.format("Using default display cap for user %s, campaign %s: %d", 
                            userId, campaignId, weeklyTracker.getRemainingDisplayCap()));
                }
            } else {
                // If there's no existing tracker, use the campaign's display cap
                weeklyTracker.setRemainingDisplayCap(campaign.getDisplayCapping());
                log.info(String.format("Setting initial display cap for user %s, campaign %s: %d", 
                        userId, campaignId, weeklyTracker.getRemainingDisplayCap()));
            }
        }
        
        // Apply the view
        weeklyTracker.setRemainingWeeklyFrequency(Math.max(0, weeklyTracker.getRemainingWeeklyFrequency() - 1));
        weeklyTracker.setRemainingDisplayCap(Math.max(0, weeklyTracker.getRemainingDisplayCap() - 1));
        weeklyTracker.setLastViewDate(currentDate);
        
        log.info(String.format("Applied view for user %s, campaign %s: weekly frequency=%d, display cap=%d", 
                userId, campaignId, weeklyTracker.getRemainingWeeklyFrequency(), 
                weeklyTracker.getRemainingDisplayCap()));
        
        // Save the tracker
        return userTrackerRepository.save(weeklyTracker);
    }
}