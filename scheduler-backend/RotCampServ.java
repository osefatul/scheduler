package com.usbank.corp.dcr.api.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.usbank.corp.dcr.api.entity.CampaignMapping;
import com.usbank.corp.dcr.api.entity.UserCampaignHistory;
import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.model.CampaignResponseDTO;
import com.usbank.corp.dcr.api.repository.CampaignRepository;
import com.usbank.corp.dcr.api.repository.UserCampaignHistoryRepository;
import com.usbank.corp.dcr.api.utils.RotationUtils;

@Service
public class RotationCampaignService {
    
    private static final Logger log = LoggerFactory.getLogger(RotationCampaignService.class);
    
    private final CampaignRepository campaignRepository;
    private final UserCampaignHistoryRepository userCampaignHistoryRepository;
    
    @Autowired
    RotationUtils rotationUtils;
    
    @Autowired
    public RotationCampaignService(CampaignRepository campaignRepository, 
                                  UserCampaignHistoryRepository userCampaignHistoryRepository) {
        this.campaignRepository = campaignRepository;
        this.userCampaignHistoryRepository = userCampaignHistoryRepository;
    }

    /**
     * Get next eligible campaign for a user, respecting rotation rules
     * 
     * @param requestDate in format yyyyMMdd
     * @param company Company identifier
     * @param userId User identifier
     * @return Next eligible campaign, or null if none available
     * @throws DataHandlingException if there's an issue with data handling
     */
    public CampaignResponseDTO getNextEligibleCampaign(String requestDate, String company, String userId) 
            throws DataHandlingException {
        
        String formattedDate = rotationUtils.convertDate(requestDate);
        Date currentDate = rotationUtils.getinDate(formattedDate);
        
        // Check if user has seen a campaign this week
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        Optional<UserCampaignHistory> userHistory = userCampaignHistoryRepository
                .findRecentUserHistory(userId, weekStartDate);
        
        if (userHistory.isPresent()) {
            // User has already seen a campaign this week
            UserCampaignHistory history = userHistory.get();
            
            if (history.getHasSubmittedForm()) {
                // User submitted a form, don't show any more campaigns this week
                throw new DataHandlingException(HttpStatus.OK.toString(), 
                        "User has already submitted a form this week");
            }
            
            if (!history.getHasClickedLearnMore()) {
                // User has seen a campaign but hasn't clicked learn more, don't show again this week
                throw new DataHandlingException(HttpStatus.OK.toString(), 
                        "User has already seen a campaign this week");
            }
            
            // User clicked learn more but hasn't submitted form, show the same campaign once more
            return getSameCampaignForUser(history.getCampaignId(), userId);
        }
        
        // Get all eligible campaigns for the company that are currently active
        List<CampaignMapping> eligibleCampaigns = getEligibleCampaigns(formattedDate, company);
        
        if (eligibleCampaigns.isEmpty()) {
            throw new DataHandlingException(HttpStatus.OK.toString(), 
                    "No eligible campaigns found for rotation");
        }
        
        // Get campaigns that the user hasn't exhausted (not reached display capping)
        List<CampaignMapping> availableCampaigns = filterCampaignsForUser(eligibleCampaigns, userId);
        
        if (availableCampaigns.isEmpty()) {
            throw new DataHandlingException(HttpStatus.OK.toString(), 
                    "User has already seen all eligible campaigns up to their display capping");
        }
        
        // Determine which campaign to show based on rotation rules
        CampaignMapping selectedCampaign = selectCampaignForRotation(availableCampaigns, currentDate);
        
        if (selectedCampaign == null) {
            throw new DataHandlingException(HttpStatus.OK.toString(), 
                    "No campaigns available for rotation at this time");
        }
        
        // Update campaign statistics and create user history
        updateCampaignStatistics(selectedCampaign, currentDate, userId);
        
        return mapToDTO(selectedCampaign);
    }
    
    /**
     * Get the list of eligible campaigns based on date and company
     */
    private List<CampaignMapping> getEligibleCampaigns(String requestDate, String company) {
        List<CampaignMapping> campaigns = campaignRepository.getEligibleCampaignsBasedonRequestDate(requestDate, company);
        
        // Filter out completed campaigns or campaigns with exhausted weekly frequency
        return campaigns.stream()
                .filter(campaign -> !"COMPLETED".equals(campaign.getStatus()))
                .filter(campaign -> campaign.getDisplayCapping() > 0)
                .collect(Collectors.toList());
    }
    
    /**
     * Filter campaigns based on user's history with them
     */
    private List<CampaignMapping> filterCampaignsForUser(List<CampaignMapping> campaigns, String userId) {
        // Get user's campaign history
        List<UserCampaignHistory> userHistory = userCampaignHistoryRepository.findAllByUserId(userId);
        
        // Group history by campaign and count occurrences
        Map<String, Long> campaignDisplayCounts = userHistory.stream()
                .collect(Collectors.groupingBy(UserCampaignHistory::getCampaignId, Collectors.counting()));
        
        // Filter campaigns that haven't reached display capping for this user
        return campaigns.stream()
                .filter(campaign -> {
                    Long displayCount = campaignDisplayCounts.getOrDefault(campaign.getId(), 0L);
                    return displayCount < campaign.getDisplayCapping();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Select which campaign to display next based on rotation rules
     */
    private CampaignMapping selectCampaignForRotation(List<CampaignMapping> campaigns, Date currentDate) {
        // First check if we need to reset weekly frequency counters
        updateWeeklyFrequencyCounts(campaigns, currentDate);
        
        // Filter campaigns that still have weekly frequency available
        List<CampaignMapping> availableThisWeek = campaigns.stream()
                .filter(campaign -> campaign.getFrequencyPerWeek() > 0)
                .collect(Collectors.toList());
        
        if (availableThisWeek.isEmpty()) {
            return null;
        }
        
        // For rotation, find the campaign that was shown least recently
        return availableThisWeek.stream()
                .sorted((c1, c2) -> {
                    // If one has never been shown, prioritize it
                    if (c1.getUpdatedDate() == null) return -1;
                    if (c2.getUpdatedDate() == null) return 1;
                    
                    // Otherwise, show the one that was shown longest ago
                    return c1.getUpdatedDate().compareTo(c2.getUpdatedDate());
                })
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Update weekly frequency counters if needed
     */
    private void updateWeeklyFrequencyCounts(List<CampaignMapping> campaigns, Date currentDate) {
        Date currentWeekStart = rotationUtils.getWeekStartDate(currentDate);
        
        for (CampaignMapping campaign : campaigns) {
            if (campaign.getUpdatedDate() == null) {
                // Never updated, nothing to reset
                continue;
            }
            
            Date lastUpdateWeekStart = rotationUtils.getWeekStartDate(campaign.getUpdatedDate());
            
            // If campaign was last updated in a previous week, reset its weekly frequency
            if (lastUpdateWeekStart.before(currentWeekStart)) {
                if (campaign.getOrginalFrequencyPerWeek() != null) {
                    campaign.setFrequencyPerWeek(campaign.getOrginalFrequencyPerWeek());
                    campaignRepository.save(campaign);
                }
            }
        }
    }
    
    /**
     * Update campaign statistics after selection for display
     */
    private void updateCampaignStatistics(CampaignMapping campaign, Date currentDate, String userId) {
        // Decrement counters
        campaign.setFrequencyPerWeek(campaign.getFrequencyPerWeek() - 1);
        
        // Back up original frequency per week if not already done
        if (campaign.getOrginalFrequencyPerWeek() == null) {
            campaign.setOrginalFrequencyPerWeek(campaign.getFrequencyPerWeek() + 1);
        }
        
        // Update timestamps
        campaign.setUpdatedDate(currentDate);
        campaign.setRequested_date(currentDate);
        campaign.setStart_week_of_requested_date(rotationUtils.getWeekStartDate(currentDate));
        
        // Update visibility status
        String visibility = campaign.getDisplayCapping() == 1 ? "COMPLETED" : "VISIBLE";
        campaign.setVisibility(visibility);
        
        // Set rotation status if needed
        if (campaign.getFrequencyPerWeek() == 0 && campaign.getDisplayCapping() > 0) {
            campaign.setRotation_status("ROTATED_RECENTLY");
        } else {
            campaign.setRotation_status(null);
        }
        
        // Save updated campaign
        campaignRepository.save(campaign);
        
        // Create user history record
        UserCampaignHistory history = new UserCampaignHistory();
        history.setUserId(userId);
        history.setCampaignId(campaign.getId());
        history.setViewDate(currentDate);
        history.setHasClickedLearnMore(false);
        history.setHasSubmittedForm(false);
        
        userCampaignHistoryRepository.save(history);
    }
    
    /**
     * Handle case where user clicked learn more but didn't submit form
     */
    private CampaignResponseDTO getSameCampaignForUser(String campaignId, String userId) {
        CampaignMapping campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + campaignId));
        
        // Update user history to track that we've shown it the second time
        UserCampaignHistory history = userCampaignHistoryRepository
                .findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new RuntimeException("User history not found"));
        
        // Mark as if they've submitted form to prevent further shows
        history.setHasSubmittedForm(true);
        userCampaignHistoryRepository.save(history);
        
        return mapToDTO(campaign);
    }
    
    /**
     * Record that user has clicked learn more on a campaign
     */
    public void recordLearnMoreClick(String campaignId, String userId) {
        UserCampaignHistory history = userCampaignHistoryRepository
                .findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new RuntimeException("User history not found"));
        
        history.setHasClickedLearnMore(true);
        userCampaignHistoryRepository.save(history);
    }
    
    /**
     * Record that user has submitted a form for a campaign
     */
    public void recordFormSubmission(String campaignId, String userId) {
        UserCampaignHistory history = userCampaignHistoryRepository
                .findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new RuntimeException("User history not found"));
        
        history.setHasSubmittedForm(true);
        userCampaignHistoryRepository.save(history);
    }
    
    /**
     * Map entity to DTO
     */
    public CampaignResponseDTO mapToDTO(CampaignMapping campaign) {
        CampaignResponseDTO response = new CampaignResponseDTO();
        response.setId(String.valueOf(campaign.getId()));
        response.setName(campaign.getName());
        response.setBannerId(campaign.getBannerId());
        response.setInsightType(campaign.getInsightType());
        response.setInsightSubType(campaign.getInsightSubType());
        response.setInsight(campaign.getInsight());
        response.setEligibleCompanies(campaign.getEligibleCompanies());
        response.setEligibleUsers(campaign.getEligibleUsers());
        response.setStartDate(campaign.getStartDate());
        response.setEndDate(campaign.getEndDate());
        response.setFrequencyPerWeek(campaign.getFrequencyPerWeek());
        response.setDisplayCapping(campaign.getDisplayCapping());
        response.setDisplayLocation(campaign.getDisplayLocation());
        response.setCreatedBy(campaign.getCreatedBy());
        response.setCreatedDate(campaign.getCreatedDate());
        response.setStatus(campaign.getStatus());
        return response;
    }
}