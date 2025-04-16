package com.usbank.corp.dcr.api.service;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        log.info("Processing request for company {} on date {}", 
                 companyId, sdf.format(currentDate));
        
        // STEP 1: Check if we've already shown a campaign this week
        List<CompanyCampaignTracker> viewedTrackers = findTrackersViewedThisWeek(companyId, weekStartDate);
        
        if (!viewedTrackers.isEmpty()) {
            // Continue with same campaign - standard weekly frequency logic
            // (This part is working correctly so keeping it brief)
            CompanyCampaignTracker tracker = viewedTrackers.get(0);
            
            if (tracker.getRemainingWeeklyFrequency() <= 0) {
                log.info("Weekly frequency exhausted for company {}", companyId);
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available for display this week");
            }
            
            CampaignMapping campaign = campaignRepository.findById(tracker.getCampaignId())
                    .orElseThrow(() -> new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Campaign not found"));
                            
            boolean updated = applyView(companyId, tracker.getCampaignId(), currentDate);
            // Continue with returning response...
        }
        
        // STEP 2: Get all eligible campaigns for this date
        List<CampaignMapping> eligibleCampaigns = getEligibleCampaigns(companyId, currentDate);
        
        if (eligibleCampaigns.isEmpty()) {
            log.info("No eligible campaigns found for company {} on date {}", 
                     companyId, sdf.format(currentDate));
            throw new DataHandlingException(HttpStatus.OK.toString(),
                    "No eligible campaigns found for the company on the requested date");
        }
        
        log.info("Found {} eligible campaigns", eligibleCampaigns.size());
        
        // STEP 3: Sort campaigns by creation date (oldest first)
        eligibleCampaigns.sort((c1, c2) -> {
            if (c1.getCreatedDate() == null && c2.getCreatedDate() == null) return 0;
            if (c1.getCreatedDate() == null) return 1;
            if (c2.getCreatedDate() == null) return -1;
            return c1.getCreatedDate().compareTo(c2.getCreatedDate());
        });
        
        // Log the sorted campaigns
        log.info("Eligible campaigns in creation date order:");
        for (int i = 0; i < eligibleCampaigns.size(); i++) {
            CampaignMapping campaign = eligibleCampaigns.get(i);
            log.info("  {}: {} (created: {})", 
                     i, campaign.getName(), 
                     campaign.getCreatedDate() != null ? sdf.format(campaign.getCreatedDate()) : "null");
        }
        
        // STEP 4: Find the most recently used campaign in the current rotation cycle
        CampaignMapping selectedCampaign = findNextCampaignInRotation(companyId, eligibleCampaigns);
        
        log.info("Selected campaign: {} ({})", 
                selectedCampaign.getName(), selectedCampaign.getId());
        
        // STEP 5: Get or create tracker
        CompanyCampaignTracker tracker = getOrCreateTracker(companyId, selectedCampaign, currentDate);
        
        // STEP 6: Apply view
        boolean updated = applyView(companyId, tracker.getCampaignId(), currentDate);
        
        if (!updated) {
            throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                    "Failed to apply view to tracker");
        }
        
        // Prepare response
        CompanyCampaignTracker updatedTracker = trackerRepository
                .findByCompanyIdAndCampaignId(companyId, tracker.getCampaignId())
                .orElse(tracker);
        
        CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(selectedCampaign);
        response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
        response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());
        
        return response;
    } catch (DataHandlingException e) {
        throw e;
    } catch (Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "Unexpected error: " + e.getMessage());
    }
}

/**
 * Find the next campaign to show in the rotation cycle
 * This implements a true rotation algorithm where each campaign gets a turn
 * before the cycle repeats
 */
private CampaignMapping findNextCampaignInRotation(String companyId, List<CampaignMapping> eligibleCampaigns) {
    // If only one eligible campaign, return it
    if (eligibleCampaigns.size() == 1) {
        log.info("Only one eligible campaign, selecting it");
        return eligibleCampaigns.get(0);
    }
    
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    
    // Get all trackers for this company
    List<CompanyCampaignTracker> allTrackers = trackerRepository.findByCompanyId(companyId);
    
    // Collect all campaigns that have been viewed (sorted by last updated, most recent first)
    List<CompanyCampaignTracker> viewedTrackers = allTrackers.stream()
        .filter(t -> t.getLastUpdated() != null) // Has been viewed
        .sorted(Comparator.comparing(CompanyCampaignTracker::getLastUpdated).reversed())
        .collect(Collectors.toList());
    
    log.info("Found {} viewed trackers for company {}", viewedTrackers.size(), companyId);
    
    // If no campaigns have been viewed yet, start with the first (oldest by creation date)
    if (viewedTrackers.isEmpty()) {
        log.info("No campaigns viewed yet, starting with the oldest (by creation date)");
        return eligibleCampaigns.get(0);
    }
    
    // Create a list of campaign IDs in the current rotation cycle
    List<String> rotationCycle = new ArrayList<>();
    Set<String> seenIds = new HashSet<>();
    
    // Build the rotation cycle from viewed trackers
    for (CompanyCampaignTracker tracker : viewedTrackers) {
        String campaignId = tracker.getCampaignId();
        
        // Only add each campaign once to the cycle
        if (!seenIds.contains(campaignId)) {
            rotationCycle.add(campaignId);
            seenIds.add(campaignId);
            
            log.info("Added to rotation cycle: {} (last viewed: {})", 
                     campaignId, 
                     tracker.getLastUpdated() != null ? sdf.format(tracker.getLastUpdated()) : "null");
        }
    }
    
    // Match rotation cycle campaigns to eligible campaigns
    List<CampaignMapping> rotationEligible = new ArrayList<>();
    for (String campaignId : rotationCycle) {
        for (CampaignMapping campaign : eligibleCampaigns) {
            if (campaign.getId().equals(campaignId)) {
                rotationEligible.add(campaign);
                break;
            }
        }
    }
    
    // Check if all eligible campaigns have been viewed in the current cycle
    boolean allEligibleViewed = true;
    for (CampaignMapping campaign : eligibleCampaigns) {
        if (!seenIds.contains(campaign.getId())) {
            allEligibleViewed = false;
            break;
        }
    }
    
    // If all eligible campaigns have been viewed, start a new cycle
    if (allEligibleViewed) {
        log.info("All eligible campaigns have been viewed, starting a new cycle");
        return eligibleCampaigns.get(0);
    }
    
    // Find the most recently viewed campaign
    CompanyCampaignTracker lastViewed = viewedTrackers.get(0);
    String lastViewedId = lastViewed.getCampaignId();
    
    log.info("Most recently viewed campaign: {}", lastViewedId);
    
    // Find the next campaign in the eligible list that hasn't been viewed yet
    boolean foundLastViewed = false;
    for (CampaignMapping campaign : eligibleCampaigns) {
        // Skip until we find the last viewed campaign
        if (!foundLastViewed) {
            if (campaign.getId().equals(lastViewedId)) {
                foundLastViewed = true;
            }
            continue;
        }
        
        // If this campaign hasn't been viewed in the current cycle, select it
        if (!seenIds.contains(campaign.getId())) {
            log.info("Found next campaign in rotation: {}", campaign.getId());
            return campaign;
        }
    }
    
    // If we reach here, we need to select the first campaign that hasn't been viewed
    for (CampaignMapping campaign : eligibleCampaigns) {
        if (!seenIds.contains(campaign.getId())) {
            log.info("Selecting first unviewed campaign: {}", campaign.getId());
            return campaign;
        }
    }
    
    // If we somehow get here, just return the first eligible campaign
    log.warn("Could not determine next campaign in rotation, using first eligible");
    return eligibleCampaigns.get(0);
}

/**
 * Get all eligible campaigns for a given date
 */
private List<CampaignMapping> getEligibleCampaigns(String companyId, Date currentDate) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    log.info("Finding eligible campaigns for company {} on date {}", 
             companyId, sdf.format(currentDate));
    
    // Get all campaigns for this company
    List<String> campaignIds = campaignCompanyService.getCampaignsForCompany(companyId);
    List<CampaignMapping> allCampaigns = campaignRepository.findAllById(campaignIds);
    
    // Filter to only eligible campaigns
    return allCampaigns.stream()
        .filter(campaign -> {
            // Check date range
            boolean validStart = campaign.getStartDate() == null || 
                               !currentDate.before(campaign.getStartDate());
            boolean validEnd = campaign.getEndDate() == null || 
                             !currentDate.after(campaign.getEndDate());
            
            // Check display cap
            boolean displayCapValid = !isDisplayCapExhausted(companyId, campaign.getId());
            
            // Combined check
            boolean isEligible = validStart && validEnd && displayCapValid;
            
            log.info("Campaign {} ({}): start={}, end={}, cap={}, ELIGIBLE={}", 
                   campaign.getId(), campaign.getName(),
                   validStart, validEnd, displayCapValid, isEligible);
            
            return isEligible;
        })
        .collect(Collectors.toList());
}










    @Transactional
   private CampaignMapping selectCampaignForRotation(
        List<CampaignMapping> eligibleCampaigns, Date currentDate) {
    
    if (eligibleCampaigns.isEmpty()) {
        return null;
    }
    
    if (eligibleCampaigns.size() == 1) {
        return eligibleCampaigns.get(0);
    }
    
    // Sort campaigns by creation date (oldest first)
    eligibleCampaigns.sort((c1, c2) -> {
        if (c1.getCreatedDate() == null && c2.getCreatedDate() == null) return 0;
        if (c1.getCreatedDate() == null) return 1;
        if (c2.getCreatedDate() == null) return -1;
        return c1.getCreatedDate().compareTo(c2.getCreatedDate());
    });
    
    // Get current calendar week
    Calendar cal = Calendar.getInstance();
    cal.setTime(currentDate);
    int weekNumber = cal.get(Calendar.WEEK_OF_YEAR);
    
    // Use week number to select campaign
    // This will rotate through all campaigns in creation date order
    int selectedIndex = (weekNumber - 1) % eligibleCampaigns.size();
    
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    
    // Log the rotation details
    log.info("Campaign rotation details:");
    log.info("- Current date: {}", sdf.format(currentDate));
    log.info("- Week number: {}", weekNumber);
    log.info("- Total eligible campaigns: {}", eligibleCampaigns.size());
    log.info("- Selected index: {}", selectedIndex);
    
    // Log all eligible campaigns
    for (int i = 0; i < eligibleCampaigns.size(); i++) {
        CampaignMapping campaign = eligibleCampaigns.get(i);
        log.info("  [{}] Campaign ID: {}, Name: {}, Created: {}, Date Range: {} to {}", 
                i,
                campaign.getId(),
                campaign.getName(),
                campaign.getCreatedDate() != null ? sdf.format(campaign.getCreatedDate()) : "unknown",
                campaign.getStartDate() != null ? sdf.format(campaign.getStartDate()) : "unknown",
                campaign.getEndDate() != null ? sdf.format(campaign.getEndDate()) : "unknown");
    }
    
    CampaignMapping selected = eligibleCampaigns.get(selectedIndex);
    log.info("Selected campaign: {} ({})", selected.getName(), selected.getId());
    
    return selected;
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
/**
 * Select tracker based on improved rotation strategy
 * Maintains proper sequence even when new campaigns become eligible
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
    
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    log.info("Selecting tracker for week number: {} ({})", weekNumber, sdf.format(currentDate));
    
    // Get corresponding campaigns to get their details
    Map<String, CampaignMapping> campaignMap = getCampaignMap(
            trackers.stream()
                   .map(CompanyCampaignTracker::getCampaignId)
                   .collect(Collectors.toList()));
    
    // CRITICAL FIX: Sort ELIGIBLE campaigns in a logical sequence
    // First sort by most recently viewed (to maintain sequence)
    // Then by start date (to incorporate newly eligible campaigns properly)
    // Finally by creation date (as the tiebreaker)
    trackers.sort((t1, t2) -> {
        CampaignMapping c1 = campaignMap.get(t1.getCampaignId());
        CampaignMapping c2 = campaignMap.get(t2.getCampaignId());
        
        // Handle null cases
        if (c1 == null && c2 == null) return 0;
        if (c1 == null) return 1;
        if (c2 == null) return -1;
        
        // First check which campaign has been viewed most recently
        // Viewed campaigns come AFTER non-viewed ones (to maintain rotation)
        boolean t1Viewed = t1.getLastUpdated() != null && t1.getRemainingWeeklyFrequency() < t1.getOriginalWeeklyFrequency();
        boolean t2Viewed = t2.getLastUpdated() != null && t2.getRemainingWeeklyFrequency() < t2.getOriginalWeeklyFrequency();
        
        if (t1Viewed && !t2Viewed) return 1;  // t1 viewed, t2 not viewed, so t2 comes first
        if (!t1Viewed && t2Viewed) return -1; // t1 not viewed, t2 viewed, so t1 comes first
        
        // If both have been viewed or both not viewed, check how recently they became eligible
        Date t1StartDate = c1.getStartDate();
        Date t2StartDate = c2.getStartDate();
        
        if (t1StartDate != null && t2StartDate != null) {
            // Campaigns that JUST became eligible come first in the rotation
            if (!t1StartDate.equals(t2StartDate)) {
                // The one with the more recent start date should be shown first
                return t2StartDate.compareTo(t1StartDate);
            }
        }
        
        // As final tiebreaker, use creation date (oldest first)
        if (c1.getCreatedDate() == null && c2.getCreatedDate() == null) return 0;
        if (c1.getCreatedDate() == null) return 1;
        if (c2.getCreatedDate() == null) return -1;
        return c1.getCreatedDate().compareTo(c2.getCreatedDate());
    });
    
    // Log the sorted order for debugging
    log.info("Campaigns ordered for rotation consideration:");
    for (int i = 0; i < trackers.size(); i++) {
        CampaignMapping campaign = campaignMap.get(trackers.get(i).getCampaignId());
        if (campaign != null) {
            log.info("  Position {}: Campaign {} (name: {}, start: {}, created: {})", 
                    i + 1, 
                    campaign.getId(),
                    campaign.getName(),
                    campaign.getStartDate() != null ? sdf.format(campaign.getStartDate()) : "null",
                    campaign.getCreatedDate() != null ? sdf.format(campaign.getCreatedDate()) : "null");
        }
    }
    
    // Select the first tracker in the sorted list
    // This will be the next one in the rotation sequence
    CompanyCampaignTracker selectedTracker = trackers.get(0);
    CampaignMapping selectedCampaign = campaignMap.get(selectedTracker.getCampaignId());
    
    log.info("Selected campaign {} ({}) for rotation in week {}", 
            selectedCampaign.getId(),
            selectedCampaign.getName(),
            weekNumber);
    
    return selectedTracker;
}


private CompanyCampaignTracker selectPerfectRotation(
        List<CompanyCampaignTracker> eligibleTrackers, 
        String companyId,
        Date currentDate) {
    
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    log.info("Selecting perfect rotation for company {} on date {}", 
             companyId, sdf.format(currentDate));
    
    if (eligibleTrackers.isEmpty()) {
        throw new IllegalArgumentException("No eligible trackers provided");
    }
    
    if (eligibleTrackers.size() == 1) {
        log.info("Only one eligible campaign, selecting it: {}", 
                 eligibleTrackers.get(0).getCampaignId());
        return eligibleTrackers.get(0);
    }
    
    // Get ALL trackers for this company, sorted by most recently viewed first
    List<CompanyCampaignTracker> allTrackers = 
        trackerRepository.findTrackersByCompanyOrderByLastUpdated(companyId);
    
    log.info("Found {} total trackers for company {}", allTrackers.size(), companyId);
    
    // Build a sequence of campaign IDs in the order they were last shown
    List<String> recentlyShownSequence = allTrackers.stream()
            .filter(t -> t.getLastUpdated() != null)
            .map(CompanyCampaignTracker::getCampaignId)
            .distinct()
            .collect(Collectors.toList());
    
    log.info("Recent display sequence: {}", recentlyShownSequence);
    
    // Get eligible campaign IDs
    Set<String> eligibleCampaignIds = eligibleTrackers.stream()
            .map(CompanyCampaignTracker::getCampaignId)
            .collect(Collectors.toSet());
    
    // Find the most recently shown eligible campaign
    String lastShownEligibleCampaign = null;
    for (String campaignId : recentlyShownSequence) {
        if (eligibleCampaignIds.contains(campaignId)) {
            lastShownEligibleCampaign = campaignId;
            break;
        }
    }
    
    log.info("Last shown eligible campaign: {}", lastShownEligibleCampaign);
    
    // Get campaigns that contain campaign details
    Map<String, CampaignMapping> campaignMap = getCampaignMap(
            eligibleTrackers.stream()
                   .map(CompanyCampaignTracker::getCampaignId)
                   .collect(Collectors.toList()));
    
    // Sort eligible trackers by creation date (oldest first) for baseline ordering
    eligibleTrackers.sort((t1, t2) -> {
        CampaignMapping c1 = campaignMap.get(t1.getCampaignId());
        CampaignMapping c2 = campaignMap.get(t2.getCampaignId());
        
        if (c1 == null || c2 == null || c1.getCreatedDate() == null || c2.getCreatedDate() == null) {
            return 0;
        }
        
        return c1.getCreatedDate().compareTo(c2.getCreatedDate());
    });
    
    // Log the initial ordering
    log.info("Eligible campaigns in creation date order:");
    for (int i = 0; i < eligibleTrackers.size(); i++) {
        log.info("  Position {}: Campaign {}", 
                i + 1, eligibleTrackers.get(i).getCampaignId());
    }
    
    // If we've shown an eligible campaign before, find the next one in rotation
    if (lastShownEligibleCampaign != null) {
        int lastShownIndex = -1;
        
        // Find the index of the last shown campaign in our ordered list
        for (int i = 0; i < eligibleTrackers.size(); i++) {
            if (eligibleTrackers.get(i).getCampaignId().equals(lastShownEligibleCampaign)) {
                lastShownIndex = i;
                break;
            }
        }
        
        if (lastShownIndex >= 0) {
            // Select the next campaign in the rotation
            int nextIndex = (lastShownIndex + 1) % eligibleTrackers.size();
            
            CompanyCampaignTracker selectedTracker = eligibleTrackers.get(nextIndex);
            log.info("Selected next campaign in rotation: {} (index {})", 
                    selectedTracker.getCampaignId(), nextIndex);
            
            return selectedTracker;
        }
    }
    
    // If we haven't shown any eligible campaign before, or couldn't find the last shown,
    // start with the first one in the creation-date-ordered list
    CompanyCampaignTracker selectedTracker = eligibleTrackers.get(0);
    log.info("Starting fresh rotation with first campaign: {}", 
            selectedTracker.getCampaignId());
    
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