/**
 * Utility class for testing campaign rotation functionality
 * Use this to verify that the "one campaign per week" rule is being enforced
 */
@Component
public class CampaignRotationTester {
    
    private static final Logger log = LoggerFactory.getLogger(CampaignRotationTester.class);
    
    private final CompanyCampaignTrackerRepository trackerRepository;
    private final RotationUtils rotationUtils;
    
    @Autowired
    public CampaignRotationTester(
            CompanyCampaignTrackerRepository trackerRepository,
            RotationUtils rotationUtils) {
        this.trackerRepository = trackerRepository;
        this.rotationUtils = rotationUtils;
    }
    
    /**
     * Test method to verify if a company has viewed any campaign this week
     * This helps debug issues with the campaign rotation
     * 
     * @param companyId Company ID to check
     * @return Status message about company's campaign views this week
     */
    public String testCompanyViewStatus(String companyId) {
        StringBuilder result = new StringBuilder();
        
        try {
            Date currentDate = new Date();
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            
            result.append("Testing company ").append(companyId).append(" on date ").append(sdf.format(currentDate))
                  .append(" (week start: ").append(sdf.format(weekStartDate)).append(")\n");
            
            // Check if any campaign has been viewed this week
            boolean hasViewedAny = trackerRepository.hasCompanyViewedAnyCampaignThisWeek(companyId, weekStartDate);
            result.append("- Company has viewed any campaign this week: ").append(hasViewedAny).append("\n");
            
            if (hasViewedAny) {
                // Get all trackers updated this week
                List<CompanyCampaignTracker> updatedTrackers = 
                    trackerRepository.findTrackersUpdatedThisWeek(companyId, weekStartDate);
                
                result.append("- Viewed campaigns this week: ").append(updatedTrackers.size()).append("\n");
                
                for (CompanyCampaignTracker tracker : updatedTrackers) {
                    result.append("  * Campaign: ").append(tracker.getCampaignId())
                          .append(", Remaining freq: ").append(tracker.getRemainingWeeklyFrequency())
                          .append(", Original freq: ").append(tracker.getOriginalWeeklyFrequency())
                          .append(", Display cap: ").append(tracker.getRemainingDisplayCap())
                          .append(", Last updated: ").append(tracker.getLastUpdated() != null ? 
                                  sdf.format(tracker.getLastUpdated()) : "null")
                          .append("\n");
                }
                
                // This is the most important check - if we have more than one campaign viewed,
                // our "one campaign per week" rule is being violated
                if (updatedTrackers.size() > 1) {
                    result.append("!!! VIOLATION: More than one campaign viewed this week !!!\n");
                }
            } else {
                // Get all active trackers
                List<CompanyCampaignTracker> activeTrackers = 
                    trackerRepository.findActiveTrackersForCompany(companyId);
                
                result.append("- Available campaigns with remaining frequency: ")
                      .append(activeTrackers.size()).append("\n");
            }
            
            return result.toString();
        } catch (Exception e) {
            log.error("Error in testCompanyViewStatus: {}", e.getMessage(), e);
            return "Error testing company view status: " + e.getMessage();
        }
    }
    
    /**
     * Force reset all trackers for a company
     * Use this to start fresh with testing
     * 
     * @param companyId Company ID to reset
     * @return Status message about the reset
     */
    @Transactional
    public String resetCompanyTrackers(String companyId) {
        try {
            Date currentDate = new Date();
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            List<CompanyCampaignTracker> trackers = trackerRepository.findByCompanyId(companyId);
            int resetCount = 0;
            
            for (CompanyCampaignTracker tracker : trackers) {
                if (tracker.getOriginalWeeklyFrequency() != null) {
                    tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                    tracker.setLastWeekReset(weekStartDate);
                    // Set last updated date to BEFORE the week start to simulate a fresh week
                    tracker.setLastUpdated(new Date(weekStartDate.getTime() - 86400000)); // 1 day before
                    trackerRepository.save(tracker);
                    resetCount++;
                }
            }
            
            return "Reset " + resetCount + " trackers for company " + companyId;
        } catch (Exception e) {
            log.error("Error resetting company trackers: {}", e.getMessage(), e);
            return "Error resetting company trackers: " + e.getMessage();
        }
    }
    
    /**
     * Simulate a week boundary to test weekly rotation
     * Advances all trackers for a company to the next week
     * 
     * @param companyId Company ID to advance
     * @return Status message about the week advancement
     */
    @Transactional
    public String advanceCompanyToNextWeek(String companyId) {
        try {
            Date currentDate = new Date();
            
            // Get current week start
            Date thisWeekStart = rotationUtils.getWeekStartDate(currentDate);
            
            // Calculate next week start (add 7 days)
            Date nextWeekStart = new Date(thisWeekStart.getTime() + (7 * 24 * 60 * 60 * 1000));
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            log.info("Advancing company {} from week {} to week {}", 
                    companyId, sdf.format(thisWeekStart), sdf.format(nextWeekStart));
            
            List<CompanyCampaignTracker> trackers = trackerRepository.findByCompanyId(companyId);
            int advancedCount = 0;
            
            for (CompanyCampaignTracker tracker : trackers) {
                if (tracker.getOriginalWeeklyFrequency() != null) {
                    // Reset frequency for the new week
                    tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                    // Set last week reset to next week start
                    tracker.setLastWeekReset(nextWeekStart);
                    // Set last updated to just before next week start
                    tracker.setLastUpdated(new Date(nextWeekStart.getTime() - 86400000));
                    trackerRepository.save(tracker);
                    advancedCount++;
                }
            }
            
            return "Advanced " + advancedCount + " trackers for company " + companyId + 
                   " to week starting " + sdf.format(nextWeekStart);
        } catch (Exception e) {
            log.error("Error advancing company to next week: {}", e.getMessage(), e);
            return "Error advancing company to next week: " + e.getMessage();
        }
    }
}