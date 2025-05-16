package com.usbank.corp.dcr.api.service;

import java.util.Date;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usbank.corp.dcr.api.entity.CampaignMapping;
import com.usbank.corp.dcr.api.entity.UserCampaignHistory;
import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.repository.CampaignRepository;
import com.usbank.corp.dcr.api.repository.UserCampaignHistoryRepository;
import com.usbank.corp.dcr.api.utils.RotationUtils;

/**
 * Service for handling user interactions with campaigns
 * Manages view history, learn more clicks, and form submissions
 */
@Service
public class UserCampaignService {
    
    private static final Logger log = LoggerFactory.getLogger(UserCampaignService.class);
    
    private final CampaignRepository campaignRepository;
    private final UserCampaignHistoryRepository userCampaignHistoryRepository;
    private final RotationUtils rotationUtils;
    
    @Autowired
    public UserCampaignService(CampaignRepository campaignRepository,
                             UserCampaignHistoryRepository userCampaignHistoryRepository,
                             RotationUtils rotationUtils) {
        this.campaignRepository = campaignRepository;
        this.userCampaignHistoryRepository = userCampaignHistoryRepository;
        this.rotationUtils = rotationUtils;
    }
    
    /**
     * Check if a user is eligible to see campaigns today
     * 
     * @param userId User identifier
     * @param currentDate Current date
     * @return true if eligible, false otherwise
     */
    public boolean isUserEligibleForCampaigns(String userId, Date currentDate) {
        // Get start of current week
        Date weekStart = rotationUtils.getWeekStartDate(currentDate);
        Date weekEnd = rotationUtils.getWeekEndDate(currentDate);
        
        // Check for existing history in this week
        Optional<UserCampaignHistory> userHistory = userCampaignHistoryRepository
                .findUserHistoryInDateRange(userId, weekStart, weekEnd);
        
        if (!userHistory.isPresent()) {
            // No history this week, user is eligible
            return true;
        }
        
        UserCampaignHistory history = userHistory.get();
        
        // If the user has submitted a form, they're not eligible for more campaigns this week
        if (history.getHasSubmittedForm()) {
            return false;
        }
        
        // If they've clicked "Learn More" but not submitted a form, they're eligible for one more view
        if (history.getHasClickedLearnMore()) {
            return true;
        }
        
        // Otherwise, they've seen a campaign this week but not clicked "Learn More"
        // They're not eligible for more campaigns
        return false;
    }
    
    /**
     * Record a campaign view for a user
     * 
     * @param userId User identifier
     * @param campaignId Campaign identifier
     * @param currentDate Current date
     * @throws DataHandlingException if recording fails
     */
    @Transactional
    public void recordCampaignView(String userId, String campaignId, Date currentDate) 
            throws DataHandlingException {
        
        CampaignMapping campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new DataHandlingException(HttpStatus.NOT_FOUND.toString(), 
                        "Campaign not found with id: " + campaignId));
        
        // Apply view to campaign
        boolean success = campaign.applyView(currentDate);
        
        if (!success) {
            throw new DataHandlingException(HttpStatus.BAD_REQUEST.toString(), 
                    "Campaign is not eligible for display");
        }
        
        // Save updated campaign
        campaignRepository.save(campaign);
        
        // Create user history record
        UserCampaignHistory history = new UserCampaignHistory();
        history.setUserId(userId);
        history.setCampaignId(campaignId);
        history.setViewDate(currentDate);
        history.setHasClickedLearnMore(false);
        history.setHasSubmittedForm(false);
        
        userCampaignHistoryRepository.save(history);
        
        log.info("Recorded campaign view for user {} on campaign {}", userId, campaignId);
    }
    
    /**
     * Record that a user clicked "Learn More" on a campaign
     * 
     * @param userId User identifier
     * @param campaignId Campaign identifier
     * @throws DataHandlingException if recording fails
     */
    @Transactional
    public void recordLearnMoreClick(String userId, String campaignId) throws DataHandlingException {
        Optional<UserCampaignHistory> historyOpt = userCampaignHistoryRepository
                .findByCampaignIdAndUserId(campaignId, userId);
        
        if (!historyOpt.isPresent()) {
            throw new DataHandlingException(HttpStatus.NOT_FOUND.toString(), 
                    "No view history found for this user and campaign");
        }
        
        UserCampaignHistory history = historyOpt.get();
        
        if (history.getHasClickedLearnMore()) {
            // Already clicked, nothing to do
            return;
        }
        
        history.setHasClickedLearnMore(true);
        userCampaignHistoryRepository.save(history);
        
        log.info("Recorded Learn More click for user {} on campaign {}", userId, campaignId);
    }
    
    /**
     * Record that a user submitted a form for a campaign
     * 
     * @param userId User identifier
     * @param campaignId Campaign identifier
     * @throws DataHandlingException if recording fails
     */
    @Transactional
    public void recordFormSubmission(String userId, String campaignId) throws DataHandlingException {
        Optional<UserCampaignHistory> historyOpt = userCampaignHistoryRepository
                .findByCampaignIdAndUserId(campaignId, userId);
        
        if (!historyOpt.isPresent()) {
            throw new DataHandlingException(HttpStatus.NOT_FOUND.toString(), 
                    "No view history found for this user and campaign");
        }
        
        UserCampaignHistory history = historyOpt.get();
        
        // Mark both flags
        history.setHasClickedLearnMore(true);
        history.setHasSubmittedForm(true);
        userCampaignHistoryRepository.save(history);
        
        log.info("Recorded form submission for user {} on campaign {}", userId, campaignId);
        
        // Update campaign if needed (e.g., for analytics)
        Optional<CampaignMapping> campaignOpt = campaignRepository.findById(campaignId);
        if (campaignOpt.isPresent()) {
            CampaignMapping campaign = campaignOpt.get();
            // Could update campaign statistics here if needed
            campaignRepository.save(campaign);
        }
    }