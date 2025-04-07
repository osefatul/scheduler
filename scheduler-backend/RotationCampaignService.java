package com.usbank.corp.dcr.api.service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usbank.corp.dcr.api.entity.CampaignMapping;
import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.model.CampaignResponseDTO;
import com.usbank.corp.dcr.api.repository.CampaignRepository;
import com.usbank.corp.dcr.api.utils.RotationUtils;

@Service
public class RotationCampaignService {
    
    private static final Logger log = LoggerFactory.getLogger(RotationCampaignService.class);
    
    private final CampaignRepository campaignRepository;
    private final CampaignCompanyService campaignCompanyService;
    private final CampaignService campaignService;
    
    @Autowired
    RotationUtils rotationUtils;
    
    @Autowired
    public RotationCampaignService(CampaignRepository campaignRepository,
                                  CampaignCompanyService campaignCompanyService,
                                  CampaignService campaignService) {
        this.campaignRepository = campaignRepository;
        this.campaignCompanyService = campaignCompanyService;
        this.campaignService = campaignService;
    }


    /**
 * Select campaign based on rotation strategy
 * This uses calendar week number to ensure equal rotation with one campaign per company per week
 * 
 * @param companyId The company identifier
 * @param currentDate Current date
 * @return Selected campaign based on rotation, or null if no eligible campaigns
 */
@Transactional(readOnly = true)
public CampaignMapping selectCampaignForCompanyByWeek(String companyId, Date currentDate) {
    // Get current week information
    String weekKey = rotationUtils.getWeekKey(currentDate);
    int weekNumber = Integer.parseInt(weekKey.split("-")[1]);
    
    log.info("Selecting campaign for company {} in week {}", companyId, weekNumber);
    
    // Get all eligible campaigns for the company
    List<CampaignMapping> eligibleCampaigns = campaignRepository
            .getEligibleCampaignsForCompany(formattedDate, companyId);
    
    if (eligibleCampaigns.isEmpty()) {
        log.info("No eligible campaigns found for company {}", companyId);
        return null;
    }
    
    // Filter out campaigns that have exhausted their displayCapping
    eligibleCampaigns = eligibleCampaigns.stream()
            .filter(campaign -> campaign.getDisplayCapping() != null && campaign.getDisplayCapping() > 0)
            .collect(Collectors.toList());
    
    if (eligibleCampaigns.isEmpty()) {
        log.info("No campaigns with available display capping for company {}", companyId);
        return null;
    }
    
    // Sort campaigns by creation date (oldest first) for consistent ordering
    eligibleCampaigns.sort((c1, c2) -> {
        if (c1.getCreatedDate() == null && c2.getCreatedDate() == null) return 0;
        if (c1.getCreatedDate() == null) return -1;
        if (c2.getCreatedDate() == null) return 1;
        return c1.getCreatedDate().compareTo(c2.getCreatedDate());
    });
    
    // Check if a campaign has been already shown this week for this company
    List<CompanyCampaignTracker> activeTrackers = trackerRepository.findByCompanyId(companyId);
    
    // If any campaign has been shown this week (has remainingWeeklyFrequency < originalWeeklyFrequency)
    // then no new campaign should be shown
    for (CompanyCampaignTracker tracker : activeTrackers) {
        if (tracker.getOriginalWeeklyFrequency() != null && 
            tracker.getRemainingWeeklyFrequency() != null &&
            tracker.getRemainingWeeklyFrequency() < tracker.getOriginalWeeklyFrequency()) {
            
            // Get this campaign if it's still eligible (has display cap)
            Optional<CampaignMapping> activeCampaign = eligibleCampaigns.stream()
                    .filter(c -> c.getId().equals(tracker.getCampaignId()))
                    .findFirst();
            
            if (activeCampaign.isPresent()) {
                log.info("Using active campaign {} for company {} this week", 
                         activeCampaign.get().getId(), companyId);
                return activeCampaign.get();
            }
        }
    }
    
    // No active campaign this week, select based on rotation
    // Calculate which campaign should be shown this week based on rotation
    // Using (weekNumber-1) to make the index 0-based for array indexing
    int campaignIndex = (weekNumber - 1) % eligibleCampaigns.size();
    
    CampaignMapping selectedCampaign = eligibleCampaigns.get(campaignIndex);
    
    log.info("Selected campaign {} by rotation for company {} in week {}", 
             selectedCampaign.getId(), companyId, weekNumber);
    
    return selectedCampaign;
}
    
    /**
     * Gets the next eligible campaign for rotation based on company
     * Uses the improved company mapping approach
     * 
     * @param requestDate Date in format yyyyMMdd
     * @param companyId Company identifier
     * @return Next eligible campaign for the company
     * @throws DataHandlingException if no eligible campaigns are found
     */
    @Transactional
    public CampaignResponseDTO getNextEligibleCampaign(String requestDate, String companyId) 
            throws DataHandlingException {
        try {
            // Convert date format
            String formattedDate = rotationUtils.convertDate(requestDate);
            Date currentDate = rotationUtils.getinDate(formattedDate);
            
            // Get the campaign for this company and week
            CampaignMapping selectedCampaign = selectCampaignForCompanyByWeek(companyId, currentDate);
            
            if (selectedCampaign == null) {
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No eligible campaigns found for the company");
            }
            
            // Get or create the tracker for this campaign-company pair
            CompanyCampaignTracker tracker = trackerService.getOrCreateTracker(companyId, selectedCampaign);
            
            // Apply the view to update metrics
            boolean updated = trackerService.applyView(companyId, selectedCampaign.getId(), currentDate);
            
            if (!updated) {
                log.warn("Failed to apply view to tracker, attempting fallback update");
                emergencyUpdateTracker(tracker.getId(), currentDate);
            }
            
            // Get a fresh copy of the campaign with updated metrics
            CampaignMapping refreshedCampaign = campaignRepository
                    .findById(selectedCampaign.getId())
                    .orElse(selectedCampaign);
            
            // Return campaign response with updated display values
            CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(refreshedCampaign);
            
            // Override the frequency values with the company-specific values from tracker
            tracker = trackerRepository.findByCompanyIdAndCampaignId(companyId, selectedCampaign.getId())
                    .orElseThrow(() -> new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Tracker not found after update"));
            
            response.setDisplayCapping(tracker.getRemainingDisplayCap());
            response.setFrequencyPerWeek(tracker.getRemainingWeeklyFrequency());
            
            return response;
        } catch (DataHandlingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in getNextEligibleCampaign: {}", e.getMessage(), e);
            throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                    "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Legacy method for updating eligible campaigns for rotations
     * This maintains backward compatibility with the original implementation
     * while using the new rotation logic internally
     * 
     * @param requestDate Date in format yyyyMMdd
     * @param companyId Company identifier
     * @return Updated campaign
     * @throws DataHandlingException if no eligible campaigns are found
     */
    @Transactional
    public CampaignResponseDTO updateEligibleCampaignsForRotations(String requestDate, String companyId) 
            throws DataHandlingException {
        
        log.info("Updating eligible campaigns for rotations: date={}, company={}", requestDate, companyId);
        
        // Get the next eligible campaign using the new rotation logic
        CampaignResponseDTO selectedCampaign = getNextEligibleCampaign(requestDate, companyId);
        
        if (selectedCampaign == null) {
            throw new DataHandlingException(HttpStatus.OK.toString(),
                    "No campaigns eligible for rotation");
        }
        
        log.info("Selected campaign {} for company {}", selectedCampaign.getId(), companyId);
        
        return selectedCampaign;
    }
    
    /**
     * Reset weekly frequency for campaigns that need it
     * 
     * @param campaigns List of campaigns to check
     * @param currentDate Current date
     */
    private void resetWeeklyFrequenciesIfNeeded(List<CampaignMapping> campaigns, Date currentDate) {
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        
        for (CampaignMapping campaign : campaigns) {
            if (campaign.getUpdatedDate() == null) {
                // Campaign never displayed, nothing to reset
                continue;
            }
            
            Date lastUpdatedWeekStart = rotationUtils.getWeekStartDate(campaign.getUpdatedDate());
            
            // If last update was in a previous week, reset weekly frequency
            if (lastUpdatedWeekStart.before(weekStartDate) && 
                campaign.getOrginalFrequencyPerWeek() != null) {
                
                log.info("Resetting weekly frequency for campaign {}", campaign.getId());
                campaign.setFrequencyPerWeek(campaign.getOrginalFrequencyPerWeek());
                campaign.setRotation_status(null); // Clear rotation status
                try {
                    campaignRepository.save(campaign);
                } catch (Exception e) {
                    log.error("Error resetting frequency for campaign {}: {}", campaign.getId(), e.getMessage());
                    // Continue with other campaigns
                }
            }
        }
    }
    
    /**
     * Select the next campaign based on rotation logic
     * Rotation is based on calendar weeks (Monday-Sunday)
     * 
     * @param campaigns List of available campaigns
     * @param currentDate Current date
     * @return Selected campaign
     */
    private CampaignMapping selectNextCampaignForRotation(List<CampaignMapping> campaigns, Date currentDate) {
        // Get current week number within the year
        // This ensures we're using calendar weeks (Monday-Sunday)
        String weekKey = rotationUtils.getWeekKey(currentDate);
        int weekNumber = Integer.parseInt(weekKey.split("-")[1]);
        
        // Log for clarity
        log.info("Selecting campaign for week number: {} of year", weekNumber);
        
        // Sort campaigns by creation date (oldest first)
        campaigns.sort((c1, c2) -> {
            if (c1.getCreatedDate() == null && c2.getCreatedDate() == null) return 0;
            if (c1.getCreatedDate() == null) return -1;
            if (c2.getCreatedDate() == null) return 1;
            return c1.getCreatedDate().compareTo(c2.getCreatedDate());
        });
        
        // Make sure weekNumber is positive for the modulo operation
        int adjustedWeekNumber = weekNumber > 0 ? weekNumber : 1;
        
        // Determine which campaign should be shown this week based on rotation
        // Campaign index = (weekNumber - 1) % total campaigns
        // This ensures campaign 0 (oldest) shows in week 1, campaign 1 in week 2, etc.
        int campaignIndex = (adjustedWeekNumber - 1) % campaigns.size();
        
        CampaignMapping selectedCampaign = campaigns.get(campaignIndex);
        log.info("Selected campaign {} (index {}) for rotation in week {}", 
                selectedCampaign.getName(), campaignIndex, weekNumber);
        
        return selectedCampaign;
    }
    
    /**
     * Update campaign after selection for display using native SQL
     * This avoids potential ORM/entity manager issues
     * 
     * @param campaign Selected campaign
     * @param currentDate Current date
     * @return true if update was successful, false otherwise
     */
    private boolean updateCampaignAfterSelection(CampaignMapping campaign, Date currentDate) {
        try {
            // Back up original frequency if not already done
            Integer originalFrequency = campaign.getOrginalFrequencyPerWeek();
            Integer currentFrequency = campaign.getFrequencyPerWeek();
            
            if (originalFrequency == null && currentFrequency != null) {
                // Update original frequency in a separate transaction
                campaignRepository.updateOriginalFrequency(campaign.getId(), currentFrequency);
                log.info("Updated original frequency to {} for campaign {}", currentFrequency, campaign.getId());
            }
            
            // Decrement counters safely
            Integer newFrequency = null;
            if (currentFrequency != null) {
                newFrequency = Math.max(0, currentFrequency - 1);
            }
            
            Integer currentCapping = campaign.getDisplayCapping();
            Integer newCapping = null;
            if (currentCapping != null) {
                newCapping = Math.max(0, currentCapping - 1);
            }
            
            // Determine visibility
            String visibility = (newCapping != null && newCapping <= 0) ? "COMPLETED" : "VISIBLE";
            
            // Determine rotation status
            String rotationStatus = null;
            if (newFrequency != null && newCapping != null && newFrequency <= 0 && newCapping > 0) {
                rotationStatus = "ROTATED_RECENTLY";
            }
            
            // Update using native query
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            int rows = campaignRepository.updateCampaignAfterSelection(
                    campaign.getId(), 
                    newFrequency, 
                    newCapping, 
                    currentDate, 
                    currentDate, 
                    weekStartDate, 
                    visibility, 
                    rotationStatus);
            
            log.info("Updated {} rows for campaign {}", rows, campaign.getId());
            
            return rows > 0;
        } catch (Exception e) {
            log.error("Error updating campaign after selection: {}", e.getMessage(), e);
            return false;
        }
    }
}