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
    @Transactional
    public CampaignResponseDTO getNextEligibleCampaign(String requestDate, String companyId) 
        throws DataHandlingException {
    try {
        // Convert date format and use this date for ALL calculations
        String formattedDate = rotationUtils.convertDate(requestDate);
        Date currentDate = rotationUtils.getinDate(formattedDate); // THIS IS THE REQUESTED DATE
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        log.info("Finding next eligible campaign for company {} on REQUESTED date {}", 
                 companyId, sdf.format(currentDate));
        log.info("Week start date for requested date: {}", sdf.format(weekStartDate));
        
        // Step 1: Check if there's an ongoing campaign for this company in this week
        List<CompanyCampaignTracker> updatedTrackers = trackerRepository.findAll().stream()
            .filter(t -> t.getCompanyId().equals(companyId))
            .filter(t -> {
                // Check if tracker's lastWeekReset is in the same week as requested date
                if (t.getLastWeekReset() == null) return false;
                Date trackerWeekStart = rotationUtils.getWeekStartDate(t.getLastWeekReset());
                return sdf.format(trackerWeekStart).equals(sdf.format(weekStartDate));
            })
            .filter(t -> t.getOriginalWeeklyFrequency() != null && 
                      t.getRemainingWeeklyFrequency() != null &&
                      t.getRemainingWeeklyFrequency() < t.getOriginalWeeklyFrequency())
            .sorted((t1, t2) -> t2.getLastUpdated().compareTo(t1.getLastUpdated())) // Most recent first
            .collect(Collectors.toList());
        
        if (!updatedTrackers.isEmpty()) {
            log.info("Company {} has viewed {} campaigns in the requested week", 
                     companyId, updatedTrackers.size());
            
            // Get the most recently viewed tracker
            CompanyCampaignTracker tracker = updatedTrackers.get(0);
            
            // Get the campaign to verify start/end dates
            CampaignMapping campaign = campaignRepository
                .findById(tracker.getCampaignId())
                .orElseThrow(() -> new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "Campaign not found for tracker"));
            
            log.info("Most recently viewed: Campaign {} (freq={}/{}, cap={})", 
                     tracker.getCampaignId(),
                     tracker.getRemainingWeeklyFrequency(),
                     tracker.getOriginalWeeklyFrequency(),
                     tracker.getRemainingDisplayCap());
            
            // CRITICAL: Check if display cap is exhausted
            if (tracker.getRemainingDisplayCap() != null && tracker.getRemainingDisplayCap() <= 0) {
                log.info("DISPLAY CAP EXHAUSTED: Campaign {} is PERMANENTLY EXPIRED", tracker.getCampaignId());
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available (display cap exhausted)");
            }
            
            // Check if campaign is within date range
            if (campaign.getStartDate() != null && currentDate.before(campaign.getStartDate())) {
                log.info("Campaign {} not started yet (start date: {})", 
                         campaign.getId(), sdf.format(campaign.getStartDate()));
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available (campaign not started yet)");
            }
            
            if (campaign.getEndDate() != null && currentDate.after(campaign.getEndDate())) {
                log.info("Campaign {} has ended (end date: {})", 
                         campaign.getId(), sdf.format(campaign.getEndDate()));
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available (campaign has ended)");
            }
            
            // Is weekly frequency exhausted?
            if (tracker.getRemainingWeeklyFrequency() <= 0) {
                log.info("FREQUENCY EXHAUSTED for company {} - no more campaigns this week", companyId);
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available for display this week");
            }
            
            // Continue showing the same campaign this week
            // Apply view to decrement counters - using the requested date!
            boolean updated = applyView(companyId, tracker.getCampaignId(), currentDate);
            
            if (!updated) {
                log.warn("Failed to apply view to tracker");
                throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "Failed to apply view to tracker");
            }
            
            // Return campaign with updated counter values
            CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(campaign);
            
            // Get fresh tracker data after the update
            CompanyCampaignTracker updatedTracker = trackerRepository
                    .findByCompanyIdAndCampaignId(companyId, tracker.getCampaignId())
                    .orElse(tracker);
            
            response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
            response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());
            
            return response;
        }
        
        // Step 2: If no campaign viewed this week, select a new one from eligible campaigns
        
        // Get all eligible campaigns for this company ON THE REQUESTED DATE
        List<CampaignMapping> eligibleCampaigns = campaignRepository
                .getEligibleCampaignsForCompany(formattedDate, companyId);
        
        if (eligibleCampaigns.isEmpty()) {
            log.info("No eligible campaigns found for company {} on date {}", 
                     companyId, sdf.format(currentDate));
            throw new DataHandlingException(HttpStatus.OK.toString(),
                    "No eligible campaigns found for the company on the requested date");
        }
        
        log.info("Found {} eligible campaigns for company {}", eligibleCampaigns.size(), companyId);
        
        // ADDITIONAL DATE VALIDATION: Verify each campaign is truly within its date range
        List<CampaignMapping> dateValidCampaigns = eligibleCampaigns.stream()
            .filter(c -> {
                boolean validStart = c.getStartDate() == null || !currentDate.before(c.getStartDate());
                boolean validEnd = c.getEndDate() == null || !currentDate.after(c.getEndDate());
                
                if (!validStart) {
                    log.info("Campaign {} not started yet (start: {})", 
                             c.getId(), c.getStartDate() != null ? sdf.format(c.getStartDate()) : "null");
                }
                
                if (!validEnd) {
                    log.info("Campaign {} already ended (end: {})", 
                             c.getId(), c.getEndDate() != null ? sdf.format(c.getEndDate()) : "null");
                }
                
                return validStart && validEnd;
            })
            .collect(Collectors.toList());
        
        if (dateValidCampaigns.isEmpty()) {
            log.info("No date-valid campaigns found for company {} on date {}", 
                     companyId, sdf.format(currentDate));
            throw new DataHandlingException(HttpStatus.OK.toString(),
                    "No campaigns available within valid date range");
        }
        
        log.info("{} campaigns are within their date range", dateValidCampaigns.size());
        
        // Ensure trackers exist for all eligible campaigns
        for (CampaignMapping campaign : dateValidCampaigns) {
            // Skip campaigns that are permanently expired due to display cap exhaustion
            if (isDisplayCapExhausted(companyId, campaign.getId())) {
                log.info("Skipping campaign {} - PERMANENTLY EXPIRED (display cap exhausted)", 
                         campaign.getId());
                continue;
            }
            
            try {
                getOrCreateTracker(companyId, campaign, currentDate);
            } catch (IllegalStateException e) {
                log.info("Skipping campaign {} - {}", campaign.getId(), e.getMessage());
                // Continue with next campaign
            }
        }
        
        // Use our helper function to get active trackers that meet all criteria
        List<CompanyCampaignTracker> activeTrackers = getActiveTrackersWithValidCapping(companyId, currentDate);
        
        if (activeTrackers.isEmpty()) {
            log.info("No active campaigns with available frequency/capping for company {}", companyId);
            throw new DataHandlingException(HttpStatus.OK.toString(),
                    "No campaigns available for display at this time");
        }
        
        log.info("Found {} active campaigns for company {}", activeTrackers.size(), companyId);
        
        // Select campaign using rotation strategy with the requested date
        CompanyCampaignTracker selectedTracker = selectTrackerByRotation(activeTrackers, currentDate);
        log.info("Selected campaign {} for company {}", selectedTracker.getCampaignId(), companyId);
        
        // Apply view to decrement counters using the requested date
        boolean updated = applyView(companyId, selectedTracker.getCampaignId(), currentDate);
        
        if (!updated) {
            log.warn("Failed to apply view to tracker");
            throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                    "Failed to apply view to tracker");
        }
        
        // Get the campaign details
        CampaignMapping selectedCampaign = campaignRepository
                .findById(selectedTracker.getCampaignId())
                .orElseThrow(() -> new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "Selected campaign not found in database"));
        
        // Return campaign response
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
    public boolean applyView(String companyId, String campaignId, Date requestedDate) {
        Optional<CompanyCampaignTracker> trackerOpt = 
                trackerRepository.findByCompanyIdAndCampaignId(companyId, campaignId);
        
        if (!trackerOpt.isPresent()) {
            log.warn("No tracker found for company {}, campaign {}", companyId, campaignId);
            return false;
        }
        
        CompanyCampaignTracker tracker = trackerOpt.get();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        log.info("BEFORE VIEW: Company={}, Campaign={}, Freq={}/{}, Cap={}, RequestedDate={}", 
                 companyId, campaignId, 
                 tracker.getRemainingWeeklyFrequency(), 
                 tracker.getOriginalWeeklyFrequency(),
                 tracker.getRemainingDisplayCap(),
                 sdf.format(requestedDate));
        
        // CRITICAL CHECK: Display cap - if 0, campaign is PERMANENTLY expired
        if (tracker.getRemainingDisplayCap() == null || tracker.getRemainingDisplayCap() <= 0) {
            log.info("DISPLAY CAP EXHAUSTED: Campaign {} is PERMANENTLY EXPIRED for company {}", 
                    campaignId, companyId);
            return false;
        }
        
        // Check weekly frequency
        if (tracker.getRemainingWeeklyFrequency() == null || tracker.getRemainingWeeklyFrequency() <= 0) {
            log.info("WEEKLY FREQUENCY EXHAUSTED: No more views for campaign {} this week", campaignId);
            return false;
        }
        
        // Ensure we're in the correct week based on requested date
        Date weekStartDate = rotationUtils.getWeekStartDate(requestedDate);
        if (tracker.getLastWeekReset() == null || 
            !sdf.format(rotationUtils.getWeekStartDate(tracker.getLastWeekReset()))
                 .equals(sdf.format(weekStartDate))) {
            
            log.info("Updating tracker's week reset date to match requested date's week");
            tracker.setLastWeekReset(weekStartDate);
        }
        
        // Decrement both counters
        tracker.setRemainingWeeklyFrequency(Math.max(0, tracker.getRemainingWeeklyFrequency() - 1));
        tracker.setRemainingDisplayCap(Math.max(0, tracker.getRemainingDisplayCap() - 1));
        tracker.setLastUpdated(requestedDate);
        
        // CRITICAL CHECK: If display cap just hit 0, log the permanent expiration
        if (tracker.getRemainingDisplayCap() == 0) {
            log.info("DISPLAY CAP NOW EXHAUSTED: Campaign {} is now PERMANENTLY EXPIRED", campaignId);
        }
        
        trackerRepository.save(tracker);
        
        log.info("AFTER VIEW: Company={}, Campaign={}, NEW Freq={}/{}, NEW Cap={}", 
                 companyId, campaignId, 
                 tracker.getRemainingWeeklyFrequency(), 
                 tracker.getOriginalWeeklyFrequency(),
                 tracker.getRemainingDisplayCap());
        
        return true;
    }


    private List<CompanyCampaignTracker> getActiveTrackersWithValidCapping(String companyId, Date currentDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        return trackerRepository.findAll().stream()
            .filter(t -> t.getCompanyId().equals(companyId))
            // CRITICAL: Display cap must be greater than 0 - if 0, campaign is PERMANENTLY expired
            .filter(t -> {
                if (t.getRemainingDisplayCap() == null || t.getRemainingDisplayCap() <= 0) {
                    log.info("Excluding campaign {} - DISPLAY CAP EXHAUSTED ({}) - PERMANENTLY EXPIRED", 
                             t.getCampaignId(), t.getRemainingDisplayCap());
                    return false;
                }
                return true;
            })
            // Weekly frequency must be greater than 0
            .filter(t -> {
                if (t.getRemainingWeeklyFrequency() == null || t.getRemainingWeeklyFrequency() <= 0) {
                    log.info("Excluding campaign {} - WEEKLY FREQUENCY EXHAUSTED ({})", 
                             t.getCampaignId(), t.getRemainingWeeklyFrequency());
                    return false;
                }
                return true;
            })
            // Campaign must be within date range
            .filter(t -> {
                // Only include if campaign exists and is within date range
                Optional<CampaignMapping> campOpt = campaignRepository.findById(t.getCampaignId());
                if (!campOpt.isPresent()) {
                    log.info("Excluding campaign {} - NOT FOUND IN DATABASE", t.getCampaignId());
                    return false;
                }
                
                CampaignMapping camp = campOpt.get();
                boolean validStart = true;
                boolean validEnd = true;
                
                if (camp.getStartDate() != null && currentDate.before(camp.getStartDate())) {
                    log.info("Excluding campaign {} - NOT STARTED YET (start: {})", 
                             camp.getId(), sdf.format(camp.getStartDate()));
                    validStart = false;
                }
                
                if (camp.getEndDate() != null && currentDate.after(camp.getEndDate())) {
                    log.info("Excluding campaign {} - ALREADY ENDED (end: {})", 
                             camp.getId(), sdf.format(camp.getEndDate()));
                    validEnd = false;
                }
                
                return validStart && validEnd;
            })
            .collect(Collectors.toList());
    }


    private boolean isDisplayCapExhausted(String companyId, String campaignId) {
        Optional<CompanyCampaignTracker> trackerOpt = 
                trackerRepository.findByCompanyIdAndCampaignId(companyId, campaignId);
        
        if (!trackerOpt.isPresent()) {
            return false; // No tracker yet, not expired
        }
        
        CompanyCampaignTracker tracker = trackerOpt.get();
        
        // CRITICAL CHECK: If display cap is 0 or less, campaign is PERMANENTLY expired
        if (tracker.getRemainingDisplayCap() != null && tracker.getRemainingDisplayCap() <= 0) {
            log.info("Campaign {} for company {} is PERMANENTLY EXPIRED - Display cap exhausted: {}", 
                     campaignId, companyId, tracker.getRemainingDisplayCap());
            return true;
        }
        
        return false;
    }
    
    /**
     * Get or create tracker for a company-campaign pair
     */
    @Transactional
    public CompanyCampaignTracker getOrCreateTracker(String companyId, CampaignMapping campaign, Date requestedDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            log.info("Getting/creating tracker for company {}, campaign {} with requested date {}", 
                     companyId, campaign.getId(), sdf.format(requestedDate));
            
            // CRITICAL CHECK: Check if campaign is permanently expired due to display cap exhaustion
            if (isDisplayCapExhausted(companyId, campaign.getId())) {
                throw new IllegalStateException("Campaign is permanently expired - display cap exhausted");
            }
            
            // Validate campaign date range
            if (campaign.getStartDate() != null && requestedDate.before(campaign.getStartDate())) {
                log.info("Campaign {} not started yet (start date: {})", 
                         campaign.getId(), sdf.format(campaign.getStartDate()));
                throw new IllegalStateException("Campaign not started yet");
            }
            
            if (campaign.getEndDate() != null && requestedDate.after(campaign.getEndDate())) {
                log.info("Campaign {} has ended (end date: {})", 
                         campaign.getId(), sdf.format(campaign.getEndDate()));
                throw new IllegalStateException("Campaign has ended");
            }
            
            // Check explicitly if the tracker exists
            Optional<CompanyCampaignTracker> existingTracker = 
                    trackerRepository.findByCompanyIdAndCampaignId(companyId, campaign.getId());
            
            if (existingTracker.isPresent()) {
                CompanyCampaignTracker tracker = existingTracker.get();
                
                // CRITICAL CHECK: Display cap must be greater than 0
                if (tracker.getRemainingDisplayCap() != null && tracker.getRemainingDisplayCap() <= 0) {
                    log.info("Campaign {} for company {} is PERMANENTLY EXPIRED - Display cap: {}", 
                             campaign.getId(), companyId, tracker.getRemainingDisplayCap());
                    throw new IllegalStateException("Campaign is permanently expired - display cap exhausted");
                }
                
                // Get week start date based on REQUESTED date, not system date
                Date weekStartDate = rotationUtils.getWeekStartDate(requestedDate);
                
                log.info("Tracker dates: lastWeekReset={}, requestedWeekStart={}", 
                         tracker.getLastWeekReset() != null ? sdf.format(tracker.getLastWeekReset()) : "null", 
                         sdf.format(weekStartDate));
                
                // ONLY reset if entering a new week AND display cap is not exhausted
                if ((tracker.getLastWeekReset() == null || 
                     !sdf.format(rotationUtils.getWeekStartDate(tracker.getLastWeekReset()))
                          .equals(sdf.format(weekStartDate))) && 
                    tracker.getOriginalWeeklyFrequency() != null) {
                    
                    // CRITICAL CHECK: Only reset if display cap is not exhausted
                    if (tracker.getRemainingDisplayCap() <= 0) {
                        log.info("NOT RESETTING frequency - DISPLAY CAP EXHAUSTED - Campaign is PERMANENTLY EXPIRED");
                        throw new IllegalStateException("Campaign is permanently expired - display cap exhausted");
                    }
                    
                    log.info("RESETTING WEEKLY FREQUENCY for company {}, campaign {} - new week", 
                            companyId, campaign.getId());
                    tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                    tracker.setLastWeekReset(weekStartDate);
                    tracker = trackerRepository.save(tracker);
                } else {
                    log.info("NOT RESETTING frequency - still in same week or already reset for this week");
                }
                
                return tracker;
            } else {
                // Create new tracker initialized with campaign values
                CompanyCampaignTracker tracker = new CompanyCampaignTracker();
                tracker.setCompanyId(companyId);
                tracker.setCampaignId(campaign.getId());
                
                // Initialize with campaign values
                tracker.setRemainingWeeklyFrequency(campaign.getFrequencyPerWeek());
                tracker.setOriginalWeeklyFrequency(campaign.getFrequencyPerWeek());
                tracker.setRemainingDisplayCap(campaign.getDisplayCapping());
                
                // Set initial dates using the REQUESTED date
                Date weekStartDate = rotationUtils.getWeekStartDate(requestedDate);
                tracker.setLastUpdated(requestedDate);
                tracker.setLastWeekReset(weekStartDate);
                
                log.info("Created new tracker for company {}, campaign {} with frequency {} and cap {} for week start {}", 
                        companyId, campaign.getId(), 
                        tracker.getRemainingWeeklyFrequency(), 
                        tracker.getRemainingDisplayCap(),
                        sdf.format(weekStartDate));
                
                return trackerRepository.save(tracker);
            }
        } catch (Exception e) {
            log.error("Error in getOrCreateTracker for company {}, campaign {}: {}", 
                    companyId, campaign.getId(), e.getMessage(), e);
            throw e;
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
    
    // STRICT ORDERING: Sort trackers by campaign creation date (oldest first)
    trackers.sort((t1, t2) -> {
        CampaignMapping c1 = campaignMap.get(t1.getCampaignId());
        CampaignMapping c2 = campaignMap.get(t2.getCampaignId());
        
        // Handle null cases
        if (c1 == null && c2 == null) return 0;
        if (c1 == null) return 1;
        if (c2 == null) return -1;
        
        // Handle null creation dates
        if (c1.getCreatedDate() == null && c2.getCreatedDate() == null) return 0;
        if (c1.getCreatedDate() == null) return 1;
        if (c2.getCreatedDate() == null) return -1;
        
        // Primary sort by creation date (oldest first)
        return c1.getCreatedDate().compareTo(c2.getCreatedDate());
    });
    
    // Log the sorted order for debugging
    log.info("Campaigns ordered by creation date (oldest first):");
    for (int i = 0; i < trackers.size(); i++) {
        CampaignMapping campaign = campaignMap.get(trackers.get(i).getCampaignId());
        log.info("  Position {}: Campaign {} (created: {})", 
                i + 1, 
                campaign != null ? campaign.getId() : "unknown",
                campaign != null && campaign.getCreatedDate() != null ? 
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(campaign.getCreatedDate()) : "unknown");
    }
    
    // Use rotation if there are multiple campaigns
    // For first week, show oldest campaign (index 0)
    // For subsequent weeks, rotate based on week number
    int adjustedWeekNumber = weekNumber > 0 ? weekNumber : 1;
    int trackerIndex = 0;  // Default to first (oldest) campaign
    
    if (trackers.size() > 1) {
        // Campaign index = (weekNumber - 1) % total campaigns
        trackerIndex = (adjustedWeekNumber - 1) % trackers.size();
    }
    
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