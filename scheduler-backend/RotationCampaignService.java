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
/**
 * Final fix for campaign rotation service
 * Strictly enforces:
 * 1. One campaign per company per week
 * 2. When frequencyPerWeek reaches zero, NO campaign for rest of week
 * 3. When displayCapping reaches zero, campaign is permanently expired
 */
@Service
public class RotationCampaignService {
    
    private static final Logger log = LoggerFactory.getLogger(RotationCampaignService.class);
    
    private final CampaignRepository campaignRepository;
    private final CampaignCompanyService campaignCompanyService;
    private final CampaignService campaignService;
    private final CompanyCampaignTrackerRepository trackerRepository;
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    RotationUtils rotationUtils;
    
    @Autowired
    public RotationCampaignService(
            CampaignRepository campaignRepository,
            CampaignCompanyService campaignCompanyService,
            CampaignService campaignService,
            CompanyCampaignTrackerRepository trackerRepository,
            JdbcTemplate jdbcTemplate) {
        this.campaignRepository = campaignRepository;
        this.campaignCompanyService = campaignCompanyService;
        this.campaignService = campaignService;
        this.trackerRepository = trackerRepository;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Gets the next eligible campaign for rotation based on company
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
            
            // ***** CRITICAL CHECK 1: Has company viewed ANY campaign this week? *****
            boolean hasActivityThisWeek = false;
            List<CompanyCampaignTracker> updatedTrackers = findTrackersUpdatedThisWeek(companyId, weekStartDate);
            
            if (!updatedTrackers.isEmpty()) {
                hasActivityThisWeek = true;
                log.info("Company {} has activity this week with {} trackers", companyId, updatedTrackers.size());
                
                // Get the most recently viewed tracker (first in the list)
                CompanyCampaignTracker mostRecentTracker = updatedTrackers.get(0);
                
                // ***** CRITICAL CHECK 2: Is weekly frequency exhausted? *****
                if (mostRecentTracker.getRemainingWeeklyFrequency() <= 0) {
                    log.info("Weekly frequency exhausted for company {} on campaign {}", 
                            companyId, mostRecentTracker.getCampaignId());
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "No campaigns available for display this week");
                }
                
                // ***** CRITICAL CHECK 3: Is display capping exhausted? *****
                if (mostRecentTracker.getRemainingDisplayCap() <= 0) {
                    log.info("Display capping exhausted for company {} on campaign {}", 
                            companyId, mostRecentTracker.getCampaignId());
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "No campaigns available for display this week");
                }
                
                // Continue showing the same campaign this week
                CampaignMapping selectedCampaign = campaignRepository
                        .findById(mostRecentTracker.getCampaignId())
                        .orElseThrow(() -> new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                                "Selected campaign not found in database"));
                
                // Apply view to decrement counters
                boolean updated = applyView(companyId, mostRecentTracker.getCampaignId(), currentDate);
                
                if (!updated) {
                    log.warn("Failed to apply view to tracker, attempting fallback update");
                    emergencyUpdateTracker(mostRecentTracker.getId(), currentDate);
                }
                
                // Return campaign with updated counter values
                CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(selectedCampaign);
                
                // Get fresh tracker data after the update
                CompanyCampaignTracker updatedTracker = trackerRepository
                        .findByCompanyIdAndCampaignId(companyId, mostRecentTracker.getCampaignId())
                        .orElse(mostRecentTracker);
                
                response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
                response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());
                
                return response;
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
                getOrCreateTracker(companyId, campaign);
            }
            
            // Step 3: Get all active trackers (with remaining weekly frequency and display cap)
            List<CompanyCampaignTracker> activeTrackers = findActiveTrackersForCompany(companyId);
            
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
                    int resetCount = resetFrequenciesForCompany(companyId);
                    log.info("Force-reset {} trackers for company {}", resetCount, companyId);
                    
                    // Try to get active trackers again
                    activeTrackers = findActiveTrackersForCompany(companyId);
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
            boolean updated = applyView(companyId, selectedTracker.getCampaignId(), currentDate);
            
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
     * Find all trackers that have been updated this week for a company
     * Orders by last updated date (most recent first)
     */
    private List<CompanyCampaignTracker> findTrackersUpdatedThisWeek(String companyId, Date weekStartDate) {
        return trackerRepository.findAll().stream()
                .filter(t -> t.getCompanyId().equals(companyId))
                .filter(t -> t.getLastWeekReset() != null && 
                            !t.getLastWeekReset().before(weekStartDate))
                .filter(t -> t.getLastUpdated() != null && 
                            !t.getLastUpdated().before(weekStartDate))
                .sorted((t1, t2) -> t2.getLastUpdated().compareTo(t1.getLastUpdated())) // Most recent first
                .collect(Collectors.toList());
    }
    
    /**
     * Find all active trackers for a company
     * Active means they have remaining weekly frequency and display cap
     */
    private List<CompanyCampaignTracker> findActiveTrackersForCompany(String companyId) {
        return trackerRepository.findAll().stream()
                .filter(t -> t.getCompanyId().equals(companyId))
                .filter(t -> t.getRemainingWeeklyFrequency() != null && 
                            t.getRemainingWeeklyFrequency() > 0)
                .filter(t -> t.getRemainingDisplayCap() != null && 
                            t.getRemainingDisplayCap() > 0)
                .collect(Collectors.toList());
    }
    
    /**
     * Apply a view to a company-campaign pair
     * Decrements both frequency and capping counters
     */
    @Transactional
    public boolean applyView(String companyId, String campaignId, Date currentDate) {
        Optional<CompanyCampaignTracker> trackerOpt = 
                trackerRepository.findByCompanyIdAndCampaignId(companyId, campaignId);
        
        if (!trackerOpt.isPresent()) {
            log.warn("No tracker found for company {}, campaign {}", companyId, campaignId);
            return false;
        }
        
        CompanyCampaignTracker tracker = trackerOpt.get();
        
        // Strict check - both must be > 0 to proceed
        if (tracker.getRemainingWeeklyFrequency() == null || tracker.getRemainingWeeklyFrequency() <= 0 ||
            tracker.getRemainingDisplayCap() == null || tracker.getRemainingDisplayCap() <= 0) {
            
            log.info("Campaign {} not eligible for company {}: freq={}, cap={}", 
                    campaignId, companyId, 
                    tracker.getRemainingWeeklyFrequency(),
                    tracker.getRemainingDisplayCap());
            return false;
        }
        
        // Decrement both counters
        tracker.setRemainingWeeklyFrequency(Math.max(0, tracker.getRemainingWeeklyFrequency() - 1));
        tracker.setRemainingDisplayCap(Math.max(0, tracker.getRemainingDisplayCap() - 1));
        tracker.setLastUpdated(currentDate);
        
        trackerRepository.save(tracker);
        
        log.info("Applied view for company {}, campaign {}. New freq: {}, new cap: {}", 
                companyId, campaignId, 
                tracker.getRemainingWeeklyFrequency(), 
                tracker.getRemainingDisplayCap());
        
        return true;
    }
    
    /**
     * Get or create tracker for a company-campaign pair
     */
    @Transactional
    public CompanyCampaignTracker getOrCreateTracker(String companyId, CampaignMapping campaign) {
        Optional<CompanyCampaignTracker> existingTracker = 
                trackerRepository.findByCompanyIdAndCampaignId(companyId, campaign.getId());
        
        if (existingTracker.isPresent()) {
            CompanyCampaignTracker tracker = existingTracker.get();
            
            // Check if weekly frequency needs reset
            Date currentDate = new Date();
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            if (tracker.getLastWeekReset() != null && 
                tracker.getLastWeekReset().before(weekStartDate) && 
                tracker.getOriginalWeeklyFrequency() != null) {
                
                log.info("Resetting weekly frequency for company {}, campaign {}", 
                        companyId, campaign.getId());
                tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                tracker.setLastWeekReset(weekStartDate);
                tracker = trackerRepository.save(tracker);
            }
            
            return tracker;
        } else {
            // Create new tracker
            CompanyCampaignTracker tracker = new CompanyCampaignTracker();
            tracker.setCompanyId(companyId);
            tracker.setCampaignId(campaign.getId());
            
            // Initialize with campaign values
            tracker.setRemainingWeeklyFrequency(campaign.getFrequencyPerWeek());
            tracker.setOriginalWeeklyFrequency(campaign.getFrequencyPerWeek());
            tracker.setRemainingDisplayCap(campaign.getDisplayCapping());
            
            // Set initial dates
            Date currentDate = new Date();
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            tracker.setLastUpdated(currentDate);
            tracker.setLastWeekReset(weekStartDate);
            
            return trackerRepository.save(tracker);
        }
    }
    
    /**
     * Reset frequencies for all trackers for a company
     */
    @Transactional
    public int resetFrequenciesForCompany(String companyId) {
        Date currentDate = new Date();
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        
        List<CompanyCampaignTracker> trackers = trackerRepository.findByCompanyId(companyId);
        int resetCount = 0;
        
        for (CompanyCampaignTracker tracker : trackers) {
            if (tracker.getOriginalWeeklyFrequency() != null) {
                tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                tracker.setLastWeekReset(weekStartDate);
                trackerRepository.save(tracker);
                resetCount++;
            }
        }
        
        return resetCount;
    }
    
    /**
     * Legacy method for updating eligible campaigns for rotations
     */
    @Transactional
    public CampaignResponseDTO updateEligibleCampaignsForRotations(String requestDate, String companyId) 
            throws DataHandlingException {
        return getNextEligibleCampaign(requestDate, companyId);
    }
    
    /**
     * Select tracker based on rotation strategy
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
        int adjustedWeekNumber = weekNumber > 0 ? weekNumber : 1;
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