package com.usbank.corp.dcr.api.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
 * Service responsible for managing the rotation of campaigns
 * Handles weekly resets and rotation scheduling
 */
@Service
public class CampaignRotationManager {
    
    private static final Logger log = LoggerFactory.getLogger(CampaignRotationManager.class);
    
    private final CampaignRepository campaignRepository;
    private final RotationUtils rotationUtils;
    
    @Autowired
    public CampaignRotationManager(CampaignRepository campaignRepository, RotationUtils rotationUtils) {
        this.campaignRepository = campaignRepository;
        this.rotationUtils = rotationUtils;
    }
    
    /**
     * Weekly scheduled task to reset campaign frequencies and manage rotation
     * Runs every Monday at 1:00 AM
     */
    @Scheduled(cron = "0 0 1 * * MON")
    @Transactional
    public void weeklyRotationReset() {
        log.info("Starting weekly campaign rotation reset");
        
        Date currentDate = new Date();
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        Date weekEndDate = rotationUtils.getWeekEndDate(currentDate);
        
        // Get all campaigns active during this week
        List<CampaignMapping> activeCampaigns = campaignRepository.findCampaignsActiveInWeek(
                weekStartDate, weekEndDate);
        
        log.info("Found {} active campaigns for the current week", activeCampaigns.size());
        
        if (activeCampaigns.isEmpty()) {
            return;
        }
        
        // Reset weekly frequency counters for all campaigns
        resetWeeklyFrequenciesForCampaigns(activeCampaigns);
        
        // Group campaigns by company to handle rotation separately for each company
        Map<String, List<CampaignMapping>> campaignsByCompany = groupCampaignsByCompany(activeCampaigns);
        
        // Apply rotation logic for each company
        for (Map.Entry<String, List<CampaignMapping>> entry : campaignsByCompany.entrySet()) {
            String company = entry.getKey();
            List<CampaignMapping> companyCampaigns = entry.getValue();
            
            log.info("Processing rotation for company: {} with {} campaigns", company, companyCampaigns.size());
            
            // Apply rotation logic - update priority/order based on week number
            applyCampaignRotation(companyCampaigns, weekStartDate);
        }
        
        log.info("Weekly campaign rotation reset completed");
    }
    
    /**
     * Reset weekly frequency counters for all campaigns
     * Restores original frequency per week values
     */
    private void resetWeeklyFrequenciesForCampaigns(List<CampaignMapping> campaigns) {
        List<String> campaignIds = new ArrayList<>();
        
        for (CampaignMapping campaign : campaigns) {
            // Only reset if needed
            if (campaign.getOrginalFrequencyPerWeek() != null && 
                !campaign.getFrequencyPerWeek().equals(campaign.getOrginalFrequencyPerWeek())) {
                
                campaign.setFrequencyPerWeek(campaign.getOrginalFrequencyPerWeek());
                campaign.setRotation_status(null); // Clear rotation status
                campaignIds.add(campaign.getId());
            }
        }
        
        if (!campaignIds.isEmpty()) {
            campaignRepository.resetWeeklyFrequency(campaignIds);
            log.info("Reset weekly frequency for {} campaigns", campaignIds.size());
        }
    }
    
    /**
     * Group campaigns by company for separate rotation handling
     */
    private Map<String, List<CampaignMapping>> groupCampaignsByCompany(List<CampaignMapping> campaigns) {
        Map<String, List<CampaignMapping>> campaignsByCompany = new HashMap<>();
        
        for (CampaignMapping campaign : campaigns) {
            // A campaign can be eligible for multiple companies (pipe-separated)
            String[] companies = campaign.getCompanyNames().split("\\|");
            
            for (String company : companies) {
                company = company.trim();
                
                if (!campaignsByCompany.containsKey(company)) {
                    campaignsByCompany.put(company, new ArrayList<>());
                }
                
                campaignsByCompany.get(company).add(campaign);
            }
        }
        
        return campaignsByCompany;
    }
    
    /**
     * Apply rotation logic for a set of campaigns
     * Updates rotation_status to control which campaign shows first
     */
    private void applyCampaignRotation(List<CampaignMapping> campaigns, Date weekStartDate) {
        if (campaigns.size() <= 1) {
            // No rotation needed for 0 or 1 campaign
            return;
        }
        
        // Sort campaigns by creation date (oldest first)
        campaigns.sort((c1, c2) -> c1.getCreatedDate().compareTo(c2.getCreatedDate()));
        
        // Get week key for determining rotation order
        String weekKey = rotationUtils.getWeekKey(weekStartDate);
        int weekNumber = Integer.parseInt(weekKey.split("-")[1]);
        
        // Calculate rotation based on week number
        // This ensures fair rotation over time
        int rotationOffset = weekNumber % campaigns.size();
        
        // Apply rotation by setting rotation status
        for (int i = 0; i < campaigns.size(); i++) {
            CampaignMapping campaign = campaigns.get(i);
            
            // Calculate position after rotation
            int rotatedPosition = (i + rotationOffset) % campaigns.size();
            
            // If it's the first campaign in rotation order, mark it so it will be shown first
            if (rotatedPosition == 0) {
                campaign.setRotation_status(null); // Will be shown first
            } else {
                // Set rotation status indicating relative position
                campaign.setRotation_status("ROTATION_POSITION_" + rotatedPosition);
            }
            
            campaignRepository.save(campaign);
        }
    }
    
    /**
     * Get the next campaign in rotation for a company
     * Used by the rotation service to implement "rotate equally" requirement
     * 
     * @param eligibleCampaigns List of all eligible campaigns
     * @param currentDate Current date
     * @return Selected campaign based on rotation rules
     */
    public CampaignMapping getNextCampaignInRotation(List<CampaignMapping> eligibleCampaigns, Date currentDate) {
        if (eligibleCampaigns.isEmpty()) {
            return null;
        }
        
        if (eligibleCampaigns.size() == 1) {
            return eligibleCampaigns.get(0);
        }
        
        // Find campaigns with no rotation status first (highest priority)
        List<CampaignMapping> highestPriorityCampaigns = eligibleCampaigns.stream()
                .filter(c -> c.getRotation_status() == null)
                .collect(Collectors.toList());
        
        if (!highestPriorityCampaigns.isEmpty()) {
            // If there are multiple campaigns with highest priority, use creation date as tiebreaker
            highestPriorityCampaigns.sort((c1, c2) -> c1.getCreatedDate().compareTo(c2.getCreatedDate()));
            return highestPriorityCampaigns.get(0);
        }
        
        // If all campaigns have rotation status, sort by position
        eligibleCampaigns.sort((c1, c2) -> {
            if (c1.getRotation_status() == null) return -1;
            if (c2.getRotation_status() == null) return 1;
            
            // Extract position number from status
            int pos1 = extractPositionFromStatus(c1.getRotation_status());
            int pos2 = extractPositionFromStatus(c2.getRotation_status());
            
            return Integer.compare(pos1, pos2);
        });
        
        return eligibleCampaigns.get(0);
    }
    
    /**
     * Extract position number from rotation status
     */
    private int extractPositionFromStatus(String rotationStatus) {
        if (rotationStatus == null || !rotationStatus.startsWith("ROTATION_POSITION_")) {
            return Integer.MAX_VALUE;
        }
        
        try {
            return Integer.parseInt(rotationStatus.substring("ROTATION_POSITION_".length()));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}