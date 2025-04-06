
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usbank.corp.dcr.api.entity.CampaignMapping;
import com.usbank.corp.dcr.api.repository.CampaignRepository;
import com.usbank.corp.dcr.api.utils.RotationUtils;

/**
 * Service for handling scheduled campaign management tasks
 * Primarily focused on weekly resets of campaign frequencies
 */
@Service
public class CampaignSchedulerService {
    
    private static final Logger log = LoggerFactory.getLogger(CampaignSchedulerService.class);
    
    private final CampaignRepository campaignRepository;
    private final RotationUtils rotationUtils;
    
    @Autowired
    public CampaignSchedulerService(CampaignRepository campaignRepository, RotationUtils rotationUtils) {
        this.campaignRepository = campaignRepository;
        this.rotationUtils = rotationUtils;
    }
    
    /**
     * Weekly scheduled task to reset campaign frequencies
     * Runs every Monday at 1:00 AM
     */
    @Scheduled(cron = "0 0 1 * * MON")
    @Transactional
    public void weeklyFrequencyReset() {
        log.info("Starting weekly campaign frequency reset");
        
        Date currentDate = new Date();
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        
        // Get all active campaigns
        List<CampaignMapping> activeCampaigns = campaignRepository.findByStatusIn(
                List.of("ACTIVE", "SCHEDULED"));
        
        log.info("Found {} active campaigns for reset", activeCampaigns.size());
        
        if (activeCampaigns.isEmpty()) {
            return;
        }
        
        int resetCount = 0;
        
        for (CampaignMapping campaign : activeCampaigns) {
            // Skip campaigns that don't need reset
            if (campaign.getOrginalFrequencyPerWeek() == null) {
                continue;
            }
            
            // Skip campaigns with no previous display
            if (campaign.getUpdatedDate() == null) {
                continue;
            }
            
            // Skip campaigns updated in current week
            Date lastUpdatedWeekStart = rotationUtils.getWeekStartDate(campaign.getUpdatedDate());
            if (!lastUpdatedWeekStart.before(weekStartDate)) {
                continue;
            }
            
            // Reset frequency to original value
            if (!campaign.getOrginalFrequencyPerWeek().equals(campaign.getFrequencyPerWeek())) {
                campaign.setFrequencyPerWeek(campaign.getOrginalFrequencyPerWeek());
                campaign.setRotation_status(null); // Clear rotation status
                campaignRepository.save(campaign);
                resetCount++;
            }
        }
        
        log.info("Reset weekly frequency for {} campaigns", resetCount);
    }
    
    /**
     * Daily task to update campaign status based on dates
     * Activates scheduled campaigns and completes expired ones
     * Runs daily at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void updateCampaignStatus() {
        log.info("Starting daily campaign status update");
        
        Date currentDate = new Date();
        
        // Get all campaigns that need status updates
        List<CampaignMapping> allCampaigns = campaignRepository.findAll();
        
        int activatedCount = 0;
        int completedCount = 0;
        
        for (CampaignMapping campaign : allCampaigns) {
            // Activate scheduled campaigns
            if ("SCHEDULED".equals(campaign.getStatus()) && 
                !currentDate.before(campaign.getStartDate())) {
                
                campaign.setStatus("ACTIVE");
                campaignRepository.save(campaign);
                activatedCount++;
            }
            
            // Complete expired campaigns
            if ("ACTIVE".equals(campaign.getStatus()) && 
                currentDate.after(campaign.getEndDate())) {
                
                campaign.setStatus("COMPLETED");
                campaign.setVisibility("COMPLETED");
                campaignRepository.save(campaign);
                completedCount++;
            }
        }
        
        log.info("Updated campaign statuses: {} activated, {} completed", 
                activatedCount, completedCount);
    }
}