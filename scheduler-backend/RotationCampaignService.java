package com.usbank.corp.dcr.api.service;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.usbank.corp.dcr.api.repository.CompanyCampaignTrackerRepository;
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
    private final CompanyCampaignTrackerRepository trackerRepository;
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    RotationUtils rotationUtils;
    
    @Autowired
    public RotationCampaignService(
            CampaignRepository campaignRepository,
            CampaignCompanyService campaignCompanyService,
            CampaignService campaignService,
            CompanyCampaignTrackerService trackerService,
            CompanyCampaignTrackerRepository trackerRepository,
            JdbcTemplate jdbcTemplate) {
        this.campaignRepository = campaignRepository;
        this.campaignCompanyService = campaignCompanyService;
        this.campaignService = campaignService;
        this.trackerService = trackerService;
        this.trackerRepository = trackerRepository;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Gets the next eligible campaign for rotation based on company
     * Enforces one campaign per company per week rule
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
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            log.info("Finding next eligible campaign for company {} on date {}", companyId, formattedDate);
            
            // Check if this company has already viewed any campaign this week
            boolean hasViewedThisWeek = trackerService.hasCompanyViewedCampaignThisWeek(companyId, currentDate);
            
            if (hasViewedThisWeek) {
                // Get the campaign that was viewed
                Optional<CompanyCampaignTracker> viewedTracker = 
                        trackerService.getViewedTrackerForCompanyThisWeek(companyId, currentDate);
                
                if (viewedTracker.isPresent()) {
                    CompanyCampaignTracker tracker = viewedTracker.get();
                    
                    log.info("Company {} has already viewed campaign {} this week", 
                            companyId, tracker.getCampaignId());
                    
                    // If frequency is exhausted, return no campaigns available
                    if (tracker.getRemainingWeeklyFrequency() <= 0) {
                        log.info("Weekly frequency exhausted for company {}", companyId);
                        throw new DataHandlingException(HttpStatus.OK.toString(),
                                "No campaigns available for display this week");
                    }
                    
                    // Continue showing the same campaign this week
                    CampaignMapping selectedCampaign = campaignRepository
                            .findById(tracker.getCampaignId())
                            .orElseThrow(() -> new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                                    "Selected campaign not found in database"));
                    
                    // Apply view to decrement counters
                    boolean updated = trackerService.applyView(companyId, tracker.getCampaignId(), currentDate);
                    
                    if (!updated) {
                        log.warn("Failed to apply view to tracker, attempting fallback update");
                        emergencyUpdateTracker(tracker.getId(), currentDate);
                    }
                    
                    // Return campaign with updated counter values
                    CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(selectedCampaign);
                    
                    // Get fresh tracker data after the update
                    CompanyCampaignTracker updatedTracker = trackerRepository
                            .findByCompanyIdAndCampaignId(companyId, tracker.getCampaignId())
                            .orElse(tracker);
                    
                    response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
                    response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());
                    
                    return response;
                } else {
                    // Company has viewed a campaign but it's no longer eligible (e.g., display capping is 0)
                    log.info("Company {} has viewed a campaign this week, but it's no longer eligible", companyId);
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "No campaigns available for display this week");
                }
            }
            
            // If no campaign viewed this week, select a new one based on rotation
            
            // Step 1: Get all eligible campaigns for this company
            List<CampaignMapping> eligibleCampaigns = campaignRepository
                    .getEligibleCampaignsForCompany(formattedDate, companyId);
            
            if (eligibleCampaigns.isEmpty()) {
                log.info("No eligible campaigns found for company {}", companyId);
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No eligible campaigns found for the company");
            }
            
            log.info("Found {} eligible campaigns for company {}", eligibleCampaigns.size(), companyId);
            
            // Step 2: Ensure trackers exist for all eligible campaigns
            for (CampaignMapping campaign : eligibleCampaigns) {
                trackerService.getOrCreateTracker(companyId, campaign);
            }
            
            // Step 3: Get all active trackers (with remaining weekly frequency and display cap)
            List<CompanyCampaignTracker> activeTrackers = trackerRepository.findActiveTrackersForCompany(companyId);
            
            // Check if we have no active trackers but do have eligible campaigns
            // This suggests frequencies need to be reset (might happen if scheduler fails)
            if (activeTrackers.isEmpty() && !eligibleCampaigns.isEmpty()) {
                log.info("No active trackers but have eligible campaigns - checking for reset need for company {}", companyId);
                
                List<CompanyCampaignTracker> allTrackers = trackerRepository.findByCompanyId(companyId);
                
                boolean needsReset = false;
                for (CompanyCampaignTracker tracker : allTrackers) {
                    if (tracker.getLastWeekReset() == null || 
                        tracker.getLastWeekReset().before(weekStartDate)) {
                        needsReset = true;
                        break;
                    }
                }
                
                if (needsReset || allTrackers.isEmpty()) {
                    // Reset frequencies for this company
                    int resetCount = trackerService.resetFrequenciesForCompany(companyId);
                    log.info("Force-reset {} trackers for company {}", resetCount, companyId);
                    
                    // Try to get active trackers again
                    activeTrackers = trackerRepository.findActiveTrackersForCompany(companyId);
                }
            }
            
            if (activeTrackers.isEmpty()) {
                log.info("No active campaigns with available frequency/capping for company {}", companyId);
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available for display this week");
            }
            
            log.info("Found {} campaigns with available frequency for company {}", 
                    activeTrackers.size(), companyId);
            
            // Step 4: Select campaign using rotation strategy
            CompanyCampaignTracker selectedTracker = selectTrackerByRotation(activeTrackers, currentDate);
            log.info("Selected campaign {} for company {}", selectedTracker.getCampaignId(), companyId);
            
            // Step 5: Apply view to decrement counters
            boolean updated = trackerService.applyView(companyId, selectedTracker.getCampaignId(), currentDate);
            
            if (!updated) {
                log.warn("Failed to apply view to tracker, attempting fallback update");
                emergencyUpdateTracker(selectedTracker.getId(), currentDate);
            }
            
            // Step 6: Get the campaign details
            CampaignMapping selectedCampaign = campaignRepository
                    .findById(selectedTracker.getCampaignId())
                    .orElseThrow(() -> new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Selected campaign not found in database"));
            
            // Step 7: Return campaign response with tracker-specific values
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
                    "last_updated = ? " +
                    "WHERE id = ?";
            
            int rows = jdbcTemplate.update(sql, new Timestamp(currentDate.getTime()), trackerId);
            
            log.info("Emergency tracker update affected {} rows", rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("Emergency tracker update failed: {}", e.getMessage(), e);
            return false;
        }
    }
}