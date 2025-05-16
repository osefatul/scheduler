@Service
@Slf4j
public class UserCampaignRotationService {

    @Autowired
    private RotationUtils rotationUtils;
    
    @Autowired
    private UserCampaignTrackerRepository userTrackerRepository;
    
    @Autowired
    private CampaignRepository campaignRepository;
    
    @Autowired
    private CampaignService campaignService;
    
    /**
     * Get the next eligible campaign for a user based on rotation rules.
     * 
     * @param requestDate Request date in format yyyyMMdd
     * @param companyId Company identifier
     * @param userId User identifier
     * @return Next eligible campaign
     * @throws DataHandlingException if there's an issue with data handling
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
            log.info("Processing request for user {} in company {} on date {}", 
                    userId, companyId, sdf.format(currentDate));
            
            // Get all eligible campaigns for the company
            List<CampaignMapping> eligibleCampaigns = getEligibleCampaigns(companyId, currentDate);
            
            if (eligibleCampaigns.isEmpty()) {
                log.info("No eligible campaigns found for company {} on date {}", 
                        companyId, sdf.format(currentDate));
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No eligible campaigns found for the company on the requested date");
            }
            
            // Get the user's current campaign state
            UserCampaignState userState = getUserCampaignState(userId, companyId, eligibleCampaigns, weekStartDate);
            
            // Check if user has a current campaign that still has available frequency
            if (userState.currentCampaign != null && userState.hasRemainingFrequency) {
                // User has a campaign with remaining frequency
                CampaignMapping campaign = userState.currentCampaign;
                
                log.info("User has remaining frequency for campaign: {} ({})", 
                        campaign.getName(), campaign.getId());
                
                // Apply the view
                UserCampaignTracker updatedTracker = applyUserView(
                        userId, companyId, campaign.getId(), currentDate, weekStartDate);
                
                // Prepare response
                CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(campaign);
                response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
                response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());
                
                return response;
            }
            
            // If we get here, either:
            // 1. User doesn't have a current campaign assigned
            // 2. User's current campaign is exhausted
            
            // Get the next available campaign for this user
            CampaignMapping nextCampaign = getNextAvailableCampaign(
                    userState.currentCampaign, eligibleCampaigns, userState.allUserTrackers);
            
            if (nextCampaign == null) {
                log.info("No more available campaigns for user {} in company {}", userId, companyId);
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No more available campaigns for this user this week");
            }
            
            log.info("Assigning next campaign to user: {} ({})", 
                    nextCampaign.getName(), nextCampaign.getId());
            
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
            log.error("Unexpected error: {}", e.getMessage(), e);
            throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                    "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Get all eligible campaigns for a company
     */
    private List<CampaignMapping> getEligibleCampaigns(String companyId, Date currentDate) {
        // Get all campaigns for the company
        List<String> campaignIds = campaignService.getCampaignsForCompany(companyId);
        
        if (campaignIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Get campaign details
        List<CampaignMapping> campaigns = campaignRepository.findAllById(campaignIds);
        
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
        if (!"ACTIVE".equals(campaign.getStatus())) {
            return false;
        }
        
        // Check date range
        return isWithinDateRange(campaign, currentDate);
    }
    
    /**
     * Check if a date is within the campaign's date range
     */
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
     * Get the user's current campaign state
     */
    private UserCampaignState getUserCampaignState(
            String userId, String companyId, List<CampaignMapping> eligibleCampaigns, Date weekStartDate) {
        
        UserCampaignState state = new UserCampaignState();
        
        // Get all trackers for this user for this week
        List<UserCampaignTracker> weeklyTrackers = userTrackerRepository
                .findByUserIdAndCompanyIdAndWeekStartDate(userId, companyId, weekStartDate);
        
        state.allUserTrackers = weeklyTrackers;
        
        if (weeklyTrackers.isEmpty()) {
            // User has no trackers for this week yet
            return state;
        }
        
        // Find the most recently viewed campaign
        UserCampaignTracker latestTracker = weeklyTrackers.stream()
                .max(Comparator.comparing(tracker -> tracker.getLastViewDate() != null ? 
                        tracker.getLastViewDate() : new Date(0)))
                .orElse(null);
        
        if (latestTracker == null) {
            return state;
        }
        
        // Get the campaign details
        Optional<CampaignMapping> campaignOpt = campaignRepository.findById(latestTracker.getCampaignId());
        if (!campaignOpt.isPresent()) {
            return state;
        }
        
        state.currentCampaign = campaignOpt.get();
        
        // Check if this campaign has remaining frequency
        boolean hasFrequency = latestTracker.getRemainingWeeklyFrequency() > 0;
        boolean hasDisplayCap = latestTracker.getRemainingDisplayCap() > 0;
        boolean isEligible = eligibleCampaigns.stream()
                .anyMatch(c -> c.getId().equals(latestTracker.getCampaignId()));
        
        state.hasRemainingFrequency = hasFrequency && hasDisplayCap && isEligible;
        
        return state;
    }
    
    /**
     * Get the next available campaign for a user
     */
    private CampaignMapping getNextAvailableCampaign(
            CampaignMapping currentCampaign, 
            List<CampaignMapping> eligibleCampaigns,
            List<UserCampaignTracker> userTrackers) {
        
        if (eligibleCampaigns.isEmpty()) {
            return null;
        }
        
        // If this is the first campaign for the user, return the first eligible one
        if (currentCampaign == null) {
            return eligibleCampaigns.get(0);
        }
        
        // Find current campaign's position in eligible campaigns
        int currentIndex = -1;
        for (int i = 0; i < eligibleCampaigns.size(); i++) {
            if (eligibleCampaigns.get(i).getId().equals(currentCampaign.getId())) {
                currentIndex = i;
                break;
            }
        }
        
        // If not found (campaign no longer eligible), start from beginning
        if (currentIndex == -1) {
            currentIndex = -1;  // Start with the first campaign
        }
        
        // Create a set of exhausted campaign IDs
        Set<String> exhaustedCampaignIds = userTrackers.stream()
                .filter(tracker -> tracker.getRemainingWeeklyFrequency() <= 0 || 
                                  tracker.getRemainingDisplayCap() <= 0)
                .map(UserCampaignTracker::getCampaignId)
                .collect(Collectors.toSet());
        
        // Find the next non-exhausted campaign
        for (int i = 1; i <= eligibleCampaigns.size(); i++) {
            int nextIndex = (currentIndex + i) % eligibleCampaigns.size();
            CampaignMapping nextCampaign = eligibleCampaigns.get(nextIndex);
            
            // Skip if this campaign is already exhausted for this user
            if (exhaustedCampaignIds.contains(nextCampaign.getId())) {
                continue;
            }
            
            return nextCampaign;
        }
        
        // If we get here, all campaigns are exhausted for this user
        return null;
    }
    
    /**
     * Apply a view for a user and return the updated tracker
     */
    private UserCampaignTracker applyUserView(
            String userId, String companyId, String campaignId, 
            Date currentDate, Date weekStartDate) {
        
        // First, get or create the tracker
        UserCampaignTracker tracker = userTrackerRepository
                .findByUserIdAndCompanyIdAndCampaignIdAndWeekStartDate(
                    userId, companyId, campaignId, weekStartDate)
                .orElse(null);
        
        // If tracker doesn't exist, create a new one
        if (tracker == null) {
            Optional<CampaignMapping> campaignOpt = campaignRepository.findById(campaignId);
            if (!campaignOpt.isPresent()) {
                throw new RuntimeException("Campaign not found: " + campaignId);
            }
            
            CampaignMapping campaign = campaignOpt.get();
            
            tracker = new UserCampaignTracker();
            tracker.setId(UUID.randomUUID().toString());
            tracker.setUserId(userId);
            tracker.setCompanyId(companyId);
            tracker.setCampaignId(campaignId);
            tracker.setWeekStartDate(weekStartDate);
            tracker.setRemainingWeeklyFrequency(campaign.getFrequencyPerWeek());
            tracker.setRemainingDisplayCap(campaign.getDisplayCapping());
        }
        
        // Apply the view
        tracker.setRemainingWeeklyFrequency(Math.max(0, tracker.getRemainingWeeklyFrequency() - 1));
        tracker.setRemainingDisplayCap(Math.max(0, tracker.getRemainingDisplayCap() - 1));
        tracker.setLastViewDate(currentDate);
        
        // Save the tracker
        return userTrackerRepository.save(tracker);
    }
    
    /**
     * Helper class to track user's campaign state
     */
    private static class UserCampaignState {
        CampaignMapping currentCampaign;
        boolean hasRemainingFrequency;
        List<UserCampaignTracker> allUserTrackers = new ArrayList<>();
    }
}