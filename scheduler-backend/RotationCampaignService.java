package com.usbank.corp.dcr.api.service;

import java.util.Date;
import java.util.List;

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
        
        // Convert date format
        String formattedDate = rotationUtils.convertDate(requestDate);
        Date currentDate = rotationUtils.getinDate(formattedDate);
        
        // Get all eligible campaigns for the company using the join query
        List<CampaignMapping> eligibleCampaigns = campaignRepository
                .getEligibleCampaignsForCompany(formattedDate, companyId);
        
        if (eligibleCampaigns.isEmpty()) {
            throw new DataHandlingException(HttpStatus.OK.toString(),
                    "No eligible campaigns found for the company");
        }
        
        // For each campaign, check if it needs frequency reset for the current week
        resetWeeklyFrequenciesIfNeeded(eligibleCampaigns, currentDate);
        
        // Filter campaigns that still have available frequency this week
        List<CampaignMapping> availableThisWeek = eligibleCampaigns.stream()
                .filter(campaign -> campaign.getFrequencyPerWeek() > 0 && campaign.getDisplayCapping() > 0)
                .toList();
        
        if (availableThisWeek.isEmpty()) {
            throw new DataHandlingException(HttpStatus.OK.toString(),
                    "No campaigns available for display this week");
        }
        
        // Apply rotation logic to select the next campaign
        CampaignMapping selectedCampaign = selectNextCampaignForRotation(availableThisWeek, currentDate);
        
        // Update the selected campaign's counters and status
        updateCampaignAfterSelection(selectedCampaign, currentDate);
        
        // Generate response DTO with company information
        return campaignService.mapToDTOWithCompanies(selectedCampaign);
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
                campaignRepository.save(campaign);
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
        campaigns.sort((c1, c2) -> c1.getCreatedDate().compareTo(c2.getCreatedDate()));
        
        // Determine which campaign should be shown this week based on rotation
        // Campaign index = (weekNumber - 1) % total campaigns
        // This ensures campaign 0 (oldest) shows in week 1, campaign 1 in week 2, etc.
        int campaignIndex = (weekNumber - 1) % campaigns.size();
        
        // Ensure index is within bounds (defensive programming)
        if (campaignIndex < 0) {
            campaignIndex = 0;
        }
        
        CampaignMapping selectedCampaign = campaigns.get(campaignIndex);
        log.info("Selected campaign {} (index {}) for rotation in week {}", 
                selectedCampaign.getName(), campaignIndex, weekNumber);
        
        return selectedCampaign;
    }
    
    /**
     * Update campaign after selection for display
     * 
     * @param campaign Selected campaign
     * @param currentDate Current date
     */
    private void updateCampaignAfterSelection(CampaignMapping campaign, Date currentDate) {
        try {
            // Back up original frequency if not already done
            if (campaign.getOrginalFrequencyPerWeek() == null && campaign.getFrequencyPerWeek() != null) {
                campaign.setOrginalFrequencyPerWeek(campaign.getFrequencyPerWeek());
            }
            
            // Decrement counters safely
            int currentFrequency = campaign.getFrequencyPerWeek() != null ? campaign.getFrequencyPerWeek() : 0;
            int currentCapping = campaign.getDisplayCapping() != null ? campaign.getDisplayCapping() : 0;
            
            campaign.setFrequencyPerWeek(Math.max(0, currentFrequency - 1));
            campaign.setDisplayCapping(Math.max(0, currentCapping - 1));
            
            // Update timestamps
            campaign.setUpdatedDate(currentDate);
            campaign.setRequested_date(currentDate);
            campaign.setStart_week_of_requested_date(rotationUtils.getWeekStartDate(currentDate));
            
            // Update visibility
            int displayCapping = campaign.getDisplayCapping() != null ? campaign.getDisplayCapping() : 0;
            String visibility = displayCapping <= 0 ? "COMPLETED" : "VISIBLE";
            campaign.setVisibility(visibility);
            
            // Set rotation status if weekly frequency is exhausted
            int frequencyPerWeek = campaign.getFrequencyPerWeek() != null ? campaign.getFrequencyPerWeek() : 0;
            if (frequencyPerWeek <= 0 && displayCapping > 0) {
                campaign.setRotation_status("ROTATED_RECENTLY");
            } else {
                campaign.setRotation_status(null);
            }
            
            // Save updated campaign
            campaignRepository.save(campaign);
            
            log.info("Updated campaign after selection: id={}, frequencyPerWeek={}, displayCapping={}, visibility={}", 
                    campaign.getId(), campaign.getFrequencyPerWeek(), campaign.getDisplayCapping(), campaign.getVisibility());
        } catch (Exception e) {
            log.error("Error updating campaign after selection: {}", e.getMessage(), e);
            throw e; // Re-throw to maintain transaction behavior
        }
    }
}