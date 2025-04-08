import java.text.SimpleDateFormat;
import java.util.List;

public @Component
public class CampaignRotationTestUtil {
    
    private static final Logger log = LoggerFactory.getLogger(CampaignRotationTestUtil.class);
    
    private final CompanyCampaignTrackerRepository trackerRepository;
    private final RotationUtils rotationUtils;
    
    @Autowired
    public CampaignRotationTestUtil(
            CompanyCampaignTrackerRepository trackerRepository,
            RotationUtils rotationUtils) {
        this.trackerRepository = trackerRepository;
        this.rotationUtils = rotationUtils;
    }
    
    /**
     * Log the current state of trackers for a company
     * Useful for debugging rotation issues
     * 
     * @param companyId Company ID to check
     */
    public void logCompanyTrackerState(String companyId) {
        try {
            List<CompanyCampaignTracker> trackers = trackerRepository.findByCompanyId(companyId);
            Date currentDate = new Date();
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            
            log.info("===== TRACKER STATE FOR COMPANY: {} =====", companyId);
            log.info("Current date: {}, Week start: {}", 
                    sdf.format(currentDate), sdf.format(weekStartDate));
            log.info("Current week key: {}", rotationUtils.getWeekKey(currentDate));
            log.info("Total trackers found: {}", trackers.size());
            
            for (CompanyCampaignTracker tracker : trackers) {
                log.info("Campaign: {}, Remaining Weekly Freq: {}/{}, Display Cap: {}, " +
                        "Last Updated: {}, Last Week Reset: {}, Viewed This Week: {}", 
                        tracker.getCampaignId(),
                        tracker.getRemainingWeeklyFrequency(),
                        tracker.getOriginalWeeklyFrequency(),
                        tracker.getRemainingDisplayCap(),
                        tracker.getLastUpdated() != null ? sdf.format(tracker.getLastUpdated()) : "null",
                        tracker.getLastWeekReset() != null ? sdf.format(tracker.getLastWeekReset()) : "null",
                        isTrackerViewedThisWeek(tracker, weekStartDate));
            }
            
            boolean viewedThisWeek = trackerRepository.hasCompanyViewedCampaignThisWeek(
                    companyId, weekStartDate);
            log.info("Company has viewed any campaign this week: {}", viewedThisWeek);
            log.info("===========================================");
        } catch (Exception e) {
            log.error("Error logging tracker state: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Check if a tracker has been viewed this week
     * 
     * @param tracker Tracker to check
     * @param weekStartDate Start date of the current week
     * @return true if viewed this week
     */
    private boolean isTrackerViewedThisWeek(CompanyCampaignTracker tracker, Date weekStartDate) {
        return tracker.getLastWeekReset() != null && 
               !tracker.getLastWeekReset().before(weekStartDate) &&
               tracker.getOriginalWeeklyFrequency() != null && 
               tracker.getRemainingWeeklyFrequency() < tracker.getOriginalWeeklyFrequency();
    }
    
    /**
     * Verify the "one campaign per week" rule for a company
     * 
     * @param companyId Company ID to check
     * @return true if the rule is correctly enforced
     */
    public boolean verifyOneCampaignPerWeekRule(String companyId) {
        try {
            Date currentDate = new Date();
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            List<CompanyCampaignTracker> viewedTrackers = trackerRepository
                    .findViewedTrackersForCompanyThisWeek(companyId, weekStartDate);
            
            // Count unique campaigns viewed this week
            long uniqueCampaignsViewedThisWeek = viewedTrackers.stream()
                    .map(CompanyCampaignTracker::getCampaignId)
                    .distinct()
                    .count();
            
            log.info("Company {} has viewed {} unique campaigns this week", 
                    companyId, uniqueCampaignsViewedThisWeek);
            
            // If more than one campaign viewed this week, the rule is broken
            boolean ruleEnforced = uniqueCampaignsViewedThisWeek <= 1;
            
            if (!ruleEnforced) {
                log.warn("RULE VIOLATION: More than one campaign viewed this week for company {}", 
                        companyId);
            }
            
            return ruleEnforced;
        } catch (Exception e) {
            log.error("Error verifying one campaign per week rule: {}", e.getMessage(), e);
            return false;
        }
    }
} 