package com.usbank.corp.dcr.api.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usbank.corp.dcr.api.entity.CampaignMapping;
import com.usbank.corp.dcr.api.entity.CompanyCampaignTracker;
import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.model.CampaignResponseDTO;
import com.usbank.corp.dcr.api.repository.CampaignRepository;
import com.usbank.corp.dcr.api.utils.RotationUtils;

/**
 * Updated service for handling campaign rotation with company-specific tracking
 */
@Service
public class RotationCampaignService {
    
    private static final Logger log = LoggerFactory.getLogger(RotationCampaignService.class);
    
    private final CampaignRepository campaignRepository;
    private final CampaignCompanyService campaignCompanyService;
    private final CampaignService campaignService;
    private final CompanyCampaignTrackerService trackerService;
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    RotationUtils rotationUtils;
    
    @Autowired
    public RotationCampaignService(
            CampaignRepository campaignRepository,
            CampaignCompanyService campaignCompanyService,
            CampaignService campaignService,
            CompanyCampaignTrackerService trackerService,
            JdbcTemplate jdbcTemplate) {
        this.campaignRepository = campaignRepository;
        this.campaignCompanyService = campaignCompanyService;
        this.campaignService = campaignService;
        this.trackerService = trackerService;
        this.jdbcTemplate = jdbcTemplate;
    }


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
     * Uses the improved company-specific tracking
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
            
            log.info("Finding next eligible campaign for company {} on date {}", companyId, formattedDate);
            
            // Check if there's already an active campaign for this company this week
            List<CompanyCampaignTracker> trackers = trackerRepository.findByCompanyId(companyId);
            
            // Get this week's start date for comparison
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            // Reset frequencies for trackers if needed (crossing to a new week)
            for (CompanyCampaignTracker tracker : trackers) {
                if (tracker.getLastWeekReset() != null && 
                    tracker.getLastWeekReset().before(weekStartDate) &&
                    tracker.getOriginalWeeklyFrequency() != null) {
                    
                    log.info("Resetting weekly frequency for company {}, campaign {}", 
                            companyId, tracker.getCampaignId());
                    tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                    tracker.setLastWeekReset(weekStartDate);
                    trackerRepository.save(tracker);
                }
            }
            
            // Find active trackers after potential resets (those with available frequency this week)
            List<CompanyCampaignTracker> activeTrackers = trackers.stream()
                    .filter(t -> t.getRemainingWeeklyFrequency() != null && t.getRemainingWeeklyFrequency() > 0)
                    .filter(t -> t.getRemainingDisplayCap() != null && t.getRemainingDisplayCap() > 0)
                    .collect(Collectors.toList());
            
            if (activeTrackers.isEmpty()) {
                log.info("No active campaigns available for company {} this week", companyId);
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available for display this week");
            }
            
            // Select one campaign based on rotation
            CompanyCampaignTracker selectedTracker = selectTrackerByRotation(activeTrackers, currentDate);
            log.info("Selected tracker for campaign {} for company {}", 
                    selectedTracker.getCampaignId(), companyId);
            
            // Apply view to decrement counters
            boolean updated = trackerService.applyView(companyId, selectedTracker.getCampaignId(), currentDate);
            
            if (!updated) {
                log.warn("Failed to apply view to tracker, attempting fallback update");
                emergencyUpdateTracker(selectedTracker.getId(), currentDate);
            }
            
            // Get the campaign details
            CampaignMapping selectedCampaign = campaignRepository
                    .findById(selectedTracker.getCampaignId())
                    .orElseThrow(() -> new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Selected campaign not found in database"));
            
            // Return campaign response with tracker-specific values
            CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(selectedCampaign);
            
            // Get fresh tracker data after the update
            CompanyCampaignTracker updatedTracker = trackerRepository
                    .findByCompanyIdAndCampaignId(companyId, selectedTracker.getCampaignId())
                    .orElse(selectedTracker);
            
            // Override the frequency values with the company-specific values
            response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
            response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());
            
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
     * Select tracker based on rotation strategy
     * 
     * @param trackers List of eligible trackers
     * @param currentDate Current date
     * @return Selected tracker based on rotation
     */
    private CompanyCampaignTracker selectTrackerByRotation(
            List<CompanyCampaignTracker> trackers, Date currentDate) {
        
        // Get current week information
        String weekKey = rotationUtils.getWeekKey(currentDate);
        int weekNumber = Integer.parseInt(weekKey.split("-")[1]);
        
        log.info("Selecting tracker for week number: {}", weekNumber);
        
        // Get corresponding campaigns to sort by creation date
        Map<String, CampaignMapping> campaignMap = getCampaignMap(
                trackers.stream()
                       .map(CompanyCampaignTracker::getCampaignId)
                       .collect(Collectors.toList()));
        
        // Sort trackers by campaign creation date (oldest first)
        trackers.sort((t1, t2) -> {
            CampaignMapping c1 = campaignMap.get(t1.getCampaignId());
            CampaignMapping c2 = campaignMap.get(t2.getCampaignId());
            if (c1 == null || c2 == null) return 0;
            if (c1.getCreatedDate() == null && c2.getCreatedDate() == null) return 0;
            if (c1.getCreatedDate() == null) return -1;
            if (c2.getCreatedDate() == null) return 1;
            return c1.getCreatedDate().compareTo(c2.getCreatedDate());
        });
        
        // Calculate which campaign should be shown this week based on rotation
        // Make sure weekNumber is positive for the modulo operation
        int adjustedWeekNumber = weekNumber > 0 ? weekNumber : 1;
        
        // Campaign index = (weekNumber - 1) % total campaigns
        int trackerIndex = (adjustedWeekNumber - 1) % trackers.size();
        
        CompanyCampaignTracker selectedTracker = trackers.get(trackerIndex);
        CampaignMapping selectedCampaign = campaignMap.get(selectedTracker.getCampaignId());
        
        log.info("Selected campaign {} (index {}) for rotation in week {}", 
                selectedCampaign != null ? selectedCampaign.getName() : "unknown", 
                trackerIndex, weekNumber);
        
        return selectedTracker;
    }
    
    /**
     * Legacy method for updating eligible campaigns for rotations
     * This maintains backward compatibility with the original implementation
     * while using the new tracker-based rotation logic
     * 
     * @param requestDate Date in format yyyyMMdd
     * @param companyId Company identifier
     * @return Updated campaign
     * @throws DataHandlingException if no eligible campaigns are found
     */
    @Transactional
    public CampaignResponseDTO updateEligibleCampaignsForRotations(String requestDate, String companyId) 
            throws DataHandlingException {
        
        log.info("Legacy call: Updating eligible campaigns for rotations: date={}, company={}", 
                requestDate, companyId);
        
        // Use the new method for rotation
        return getNextEligibleCampaign(requestDate, companyId);
    }
    
    /**
     * Select tracker based on rotation strategy
     * This uses calendar week number to ensure equal rotation
     * 
     * @param trackers List of eligible trackers
     * @param currentDate Current date
     * @return Selected tracker based on rotation
     */
    private CompanyCampaignTracker selectTrackerByRotationStrategy(
            List<CompanyCampaignTracker> trackers, Date currentDate) {
        
        // Get current week information
        String weekKey = rotationUtils.getWeekKey(currentDate);
        int weekNumber = Integer.parseInt(weekKey.split("-")[1]);
        
        log.info("Selecting campaign for week number: {}", weekNumber);
        
        // Get corresponding campaigns to sort by creation date
        Map<String, CampaignMapping> campaignMap = getCampaignMap(
                trackers.stream()
                       .map(CompanyCampaignTracker::getCampaignId)
                       .collect(Collectors.toList()));
        
        // Sort trackers by campaign creation date (oldest first)
        trackers.sort((t1, t2) -> {
            CampaignMapping c1 = campaignMap.get(t1.getCampaignId());
            CampaignMapping c2 = campaignMap.get(t2.getCampaignId());
            if (c1 == null || c2 == null) return 0;
            if (c1.getCreatedDate() == null && c2.getCreatedDate() == null) return 0;
            if (c1.getCreatedDate() == null) return -1;
            if (c2.getCreatedDate() == null) return 1;
            return c1.getCreatedDate().compareTo(c2.getCreatedDate());
        });
        
        // Calculate which campaign should be shown this week based on rotation
        // Make sure weekNumber is positive for the modulo operation
        int adjustedWeekNumber = weekNumber > 0 ? weekNumber : 1;
        
        // Campaign index = (weekNumber - 1) % total campaigns
        int trackerIndex = (adjustedWeekNumber - 1) % trackers.size();
        
        CompanyCampaignTracker selectedTracker = trackers.get(trackerIndex);
        CampaignMapping selectedCampaign = campaignMap.get(selectedTracker.getCampaignId());
        
        log.info("Selected campaign {} (index {}) for rotation in week {}", 
                selectedCampaign != null ? selectedCampaign.getName() : "unknown", 
                trackerIndex, weekNumber);
        
        return selectedTracker;
    }
    
    /**
     * Get a map of campaign ID to campaign entity
     */
    private Map<String, CampaignMapping> getCampaignMap(List<String> campaignIds) {
        return campaignRepository.findAllById(campaignIds)
                .stream()
                .collect(Collectors.toMap(CampaignMapping::getId, campaign -> campaign));
    }
    
    /**
     * Emergency backup method to update tracker using direct JDBC
     * This bypasses all JPA/Hibernate issues
     */
    private boolean emergencyUpdateTracker(String trackerId, Date currentDate) {
        try {
            log.info("Attempting emergency direct JDBC update for tracker ID: {}", trackerId);
            
            // Basic update to decrement counters
            String sql = "UPDATE company_campaign_tracker SET " +
                    "remaining_weekly_frequency = GREATEST(0, remaining_weekly_frequency - 1), " +
                    "remaining_display_cap = GREATEST(0, remaining_display_cap - 1), " +
                    "last_updated = ?, " +
                    "rotation_status = CASE " +
                    "  WHEN remaining_weekly_frequency <= 1 AND remaining_display_cap > 1 THEN 'ROTATED_RECENTLY' " +
                    "  ELSE rotation_status " +
                    "END " +
                    "WHERE id = ?";
            
            int rows = jdbcTemplate.update(sql, new java.sql.Timestamp(currentDate.getTime()), trackerId);
            
            log.info("Emergency tracker update affected {} rows", rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("Emergency tracker update failed: {}", e.getMessage(), e);
            return false;
        }
    }
}