package service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RotationCampaignServiceUpdated {

  @Transactional
  public CampaignResponseDTO getNextEligibleCampaign(String requestDate, String companyId)
      throws DataHandlingException {
    try {
      // Convert date format
      String formattedDate = rotationUtils.convertDate(requestDate);
      Date currentDate = rotationUtils.getinDate(formattedDate);
      Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);

      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      log.info("Processing request for company {} on date {}", companyId, sdf.format(currentDate));
      log.info("Week start date: {}", sdf.format(weekStartDate));

      // STEP 1: Check if we've already assigned a campaign for this week
      CampaignMapping assignedCampaign = getAssignedCampaignForWeek(companyId, weekStartDate);

      if (assignedCampaign != null) {
        log.info("Found already assigned campaign for this week: {} ({})",
            assignedCampaign.getName(), assignedCampaign.getId());

        // Get tracker for assigned campaign
        CompanyCampaignTracker tracker = trackerRepository
            .findByCompanyIdAndCampaignId(companyId, assignedCampaign.getId())
            .orElseThrow(() -> new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "Tracker not found for assigned campaign"));

        // Check if frequency is exhausted
        if (tracker.getRemainingWeeklyFrequency() <= 0) {
          log.info("Weekly frequency exhausted for campaign {}", assignedCampaign.getName());
          throw new DataHandlingException(HttpStatus.OK.toString(),
              "No more views available for this week");
        }

        // Check if display cap is exhausted
        if (tracker.getRemainingDisplayCap() <= 0) {
          log.info("Display cap exhausted for campaign {}", assignedCampaign.getName());
          throw new DataHandlingException(HttpStatus.OK.toString(),
              "This campaign has reached its display cap limit");
        }

        // Check if still in date range
        boolean validDate = isWithinDateRange(assignedCampaign, currentDate);
        if (!validDate) {
          log.info("Assigned campaign is no longer in valid date range");
          throw new DataHandlingException(HttpStatus.OK.toString(),
              "No campaigns available in valid date range");
        }

        // Apply view
        boolean updated = applyView(companyId, assignedCampaign.getId(), currentDate);

        if (!updated) {
          log.warn("Failed to apply view to tracker");
          throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
              "Failed to apply view to tracker");
        }

        // Get updated tracker
        CompanyCampaignTracker updatedTracker = trackerRepository
            .findByCompanyIdAndCampaignId(companyId, assignedCampaign.getId())
            .orElse(tracker);

        // Prepare response
        CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(assignedCampaign);
        response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
        response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());

        return response;
      }

      // STEP 2: No campaign assigned for this week, select a new one
      log.info("No campaign assigned for this week, selecting next in rotation");

      // Get campaigns eligible for rotation
      List<CampaignMapping> eligibleCampaigns = getEligibleCampaigns(companyId, currentDate);

      if (eligibleCampaigns.isEmpty()) {
        log.info("No eligible campaigns found for company {} on date {}",
            companyId, sdf.format(currentDate));
        throw new DataHandlingException(HttpStatus.OK.toString(),
            "No eligible campaigns found for the company on the requested date");
      }

      log.info("Found {} eligible campaigns", eligibleCampaigns.size());

      // STEP 3: Get last assigned campaign from previous weeks
      CampaignMapping lastAssignedCampaign = getLastAssignedCampaign(companyId);

      // STEP 4: Select next campaign in rotation
      CampaignMapping selectedCampaign = selectNextCampaignInRotation(
          eligibleCampaigns, lastAssignedCampaign);

      log.info("Selected next campaign in rotation: {} ({})",
          selectedCampaign.getName(), selectedCampaign.getId());

      // STEP 5: Mark this campaign as assigned for this week
      assignCampaignForWeek(companyId, selectedCampaign, weekStartDate);

      // STEP 6: Get or create tracker
      CompanyCampaignTracker tracker = getOrCreateTracker(companyId, selectedCampaign, currentDate);

      // STEP 7: Apply view
      boolean updated = applyView(companyId, selectedCampaign.getId(), currentDate);

      if (!updated) {
        log.warn("Failed to apply view to tracker");
        throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
            "Failed to apply view to tracker");
      }

      // Get updated tracker
      CompanyCampaignTracker updatedTracker = trackerRepository
          .findByCompanyIdAndCampaignId(companyId, selectedCampaign.getId())
          .orElse(tracker);

      // Prepare response
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

  private CampaignMapping getAssignedCampaignForWeek(String companyId, Date weekStartDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        // Find trackers that were last reset this week
        List<CompanyCampaignTracker> weekTrackers = trackerRepository.findAll().stream()
            .filter(t -> t.getCompanyId().equals(companyId))
            .filter(t -> {
                if (t.getLastWeekReset() == null)
                    return false;
                Date trackerWeekStart = rotationUtils.getWeekStartDate(t.getLastWeekReset());
                return sdf.format(trackerWeekStart).equals(sdf.format(weekStartDate));
            })
            .collect(Collectors.toList());
            
        if (weekTrackers.isEmpty()) {
            log.info(format:"No trackers found for week starting {}", sdf.format(weekStartDate));
            return null;
        }
        
        Optional<CompanyCampaignTracker> weeklyAssignedTracker = weekTrackers.stream()
            .filter(t -> t.getLastUpdated() != null) // Has been viewed
            .min(Comparator.comparing(CompanyCampaignTracker::getLastUpdated)); // First viewed in week
            
        if (!weeklyAssignedTracker.isPresent()) {
            log.info(format:"No assigned campaign found for week starting {}", sdf.format(weekStartDate));
            return null;
        }
        
        // Get the campaign
        String campaignId = weeklyAssignedTracker.get().getCampaignId();
        Optional<CampaignMapping> campaign = campaignRepository.findById(campaignId);
        
        if (!campaign.isPresent()) {
            log.warn(format:"Campaign not found for tracker: {}", campaignId);
            return null;
        }
        
        log.info(format:"Found assigned campaign for week starting {}: {} ({})",
            sdf.format(weekStartDate), campaign.get().getName(), campaignId);
            
        return campaign.get();
    }

  private CampaignMapping getLastAssignedCampaign(String companyId) {
        List<CompanyCampaignTracker> allTrackers = trackerRepository.findAll().stream()
            .filter(t -> t.getCompanyId().equals(companyId))
            .filter(t -> t.getLastUpdated() != null) // Has been viewed before
            .sorted(Comparator.comparing(CompanyCampaignTracker::getLastUpdated).reversed()) // Most recent first
            .collect(Collectors.toList());
            
        if (allTrackers.isEmpty()) {
            log.info(format:"No previous campaign assignments found for company {}", companyId);
            return null;
        }
        
        // Get the most recently viewed campaign
        String lastCampaignId = allTrackers.get(index:0).getCampaignId();
        Optional<CampaignMapping> lastCampaign = campaignRepository.findById(lastCampaignId);
        
        if (!lastCampaign.isPresent()) {
            log.warn(format:"Last campaign not found: {}", lastCampaignId);
            return null;
        }
        
        log.info(format:"Last assigned campaign was: {} ({})",
            lastCampaign.get().getName(), lastCampaignId);
            
        return lastCampaign.get();
    }

  private CampaignMapping selectNextCampaignInRotation(List<CampaignMapping> eligibleCampaigns, CampaignMapping lastAssignedCampaign) {
        if (lastAssignedCampaign == null || eligibleCampaigns.size() == 1) {
            log.info(msg:"Starting rotation with first eligible campaign");
            
            eligibleCampaigns.sort((c1, c2) -> {
                if (c1.getCreatedDate() == null && c2.getCreatedDate() == null)
                    return 0;
                if (c1.getCreatedDate() == null)
                    return 1;
                if (c2.getCreatedDate() == null)
                    return -1;
                return c1.getCreatedDate().compareTo(c2.getCreatedDate());
            });
            
            return eligibleCampaigns.get(index:0);
        }
        
        eligibleCampaigns.sort((c1, c2) -> {
            if (c1.getCreatedDate() == null && c2.getCreatedDate() == null)
                return 0;
            if (c1.getCreatedDate() == null)
                return 1;
            if (c2.getCreatedDate() == null)
                return -1;
            return c1.getCreatedDate().compareTo(c2.getCreatedDate());
        });
        
        // Log sorted order
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        log.info(msg:"Eligible campaigns in creation date order:");
        for (int i = 0; i < eligibleCampaigns.size(); i++) {
            CampaignMapping campaign = eligibleCampaigns.get(i);
            log.info(format:" {}: {} (created: {})",
                i, campaign.getName(),
                campaign.getCreatedDate() != null ? sdf.format(campaign.getCreatedDate()) : "null");
        }
        
        // Find index of last assigned campaign
        int lastIndex = -1;
        for (int i = 0; i < eligibleCampaigns.size(); i++) {
            if (eligibleCampaigns.get(i).getId().equals(lastAssignedCampaign.getId())) {
                lastIndex = i;
                break;
            }
        }
        
        // If last campaign is not in eligible list, start with first
        if (lastIndex == -1) {
            log.info(msg:"Last campaign not in eligible list, starting with first");
            return eligibleCampaigns.get(index:0);
        }
        
        // Select next campaign in rotation (wrap around if at end)
        int nextIndex = (lastIndex + 1) % eligibleCampaigns.size();
        CampaignMapping nextCampaign = eligibleCampaigns.get(nextIndex);
        
        log.info(format:"Last campaign index: {}, next campaign index: {}", lastIndex, nextIndex);
        log.info(format:"Next campaign in rotation: {} ({})",
            nextCampaign.getName(), nextCampaign.getId());
            
        return nextCampaign;
    }

  @Transactional
    private void assignCampaignForWeek(String companyId, CampaignMapping campaign, Date weekStartDate) {
        log.info(format:"Assigning campaign {} ({}) for week starting {}",
            campaign.getName(), campaign.getId(),
            new SimpleDateFormat("yyyy-MM-dd").format(weekStartDate));
            
        CompanyCampaignTracker tracker = getOrCreateTracker(companyId, campaign, weekStartDate);
        
        if (tracker.getLastWeekReset() == null ||
                !rotationUtils.getWeekStartDate(tracker.getLastWeekReset())
                 .equals(weekStartDate)) {
            tracker.setLastWeekReset(weekStartDate);
            trackerRepository.save(tracker);
        }
    }

  private List<CampaignMapping> getEligibleCampaigns(String companyId, Date currentDate) {
        List<String> campaignIds = campaignCompanyService.getCampaignsForCompany(companyId);
        List<CampaignMapping> allCampaigns = campaignRepository.findAllById(campaignIds);
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        log.info(format:"Found {} total campaigns for company {}", allCampaigns.size(), companyId);
        
        // filter to only eligible campaigns
        return allCampaigns.stream()
            .filter(campaign -> {
                boolean validDate = isWithinDateRange(campaign, currentDate);
                boolean displayCapAvailable = !isDisplayCapExhausted(companyId, campaign.getId());
                boolean isEligible = validDate && displayCapAvailable;
                
                log.info(format:"Campaign {} ({}): dateValid={}, capAvailable={}, ELIGIBLE={}",
                    campaign.getId(), campaign.getName(),
                    validDate, displayCapAvailable, isEligible);
                    
                return isEligible;
            })
            .collect(Collectors.toList());
    }

  private boolean isWithinDateRange(CampaignMapping campaign, Date currentDate) {
    boolean validStart = campaign.getStartDate() == null ||
        !currentDate.before(campaign.getStartDate());
    boolean validEnd = campaign.getEndDate() == null ||
        !currentDate.after(campaign.getEndDate());
    return validStart && validEnd;
  }

  public boolean isDisplayCapExhausted(String companyId, String campaignId) {
        CompanyCampaignTracker tracker = trackerRepository
            .findByCompanyIdAndCampaignId(companyId, campaignId)
            .orElse(other:null);
            
        if (tracker == null) {
            return false;
        }
        
        return tracker.getRemainingDisplayCap() != null && tracker.getRemainingDisplayCap() <= 0;
    }

  @Transactional
    public boolean applyView(String companyId, String campaignId, Date currentDate) {
        Optional<CompanyCampaignTracker> trackerOpt = trackerRepository.findByCompanyIdAndCampaignId(companyId,
            campaignId);
            
        if (!trackerOpt.isPresent()) {
            log.warn(format:"No tracker found for company {}, campaign {}", companyId, campaignId);
            return false;
        }
        
        CompanyCampaignTracker tracker = trackerOpt.get();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        log.info(format:"Applying view to tracker: company={}, campaign={}, freq={}/{}, cap={}",
            companyId, campaignId,
            tracker.getRemainingWeeklyFrequency(),
            tracker.getOriginalWeeklyFrequency(),
            tracker.getRemainingDisplayCap());
            
        if (tracker.getRemainingWeeklyFrequency() == null || tracker.getRemainingWeeklyFrequency() <= 0) {
            log.info(msg:"Cannot apply view - frequency already exhausted");
            return false;
        }
        
        if (tracker.getRemainingDisplayCap() == null || tracker.getRemainingDisplayCap() <= 0) {
            log.info(msg:"Cannot apply view - display cap already exhausted");
            return false;
        }
        
        tracker.setRemainingWeeklyFrequency(Math.max(0, tracker.getRemainingWeeklyFrequency() - 1));
        tracker.setRemainingDisplayCap(Math.max(0, tracker.getRemainingDisplayCap() - 1));
        tracker.setLastUpdated(currentDate);
        
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        if (tracker.getLastWeekReset() == null ||
            !sdf.format(rotationUtils.getWeekStartDate(tracker.getLastWeekReset()))
             .equals(sdf.format(weekStartDate))) {
            tracker.setLastWeekReset(weekStartDate);
        }
        
        trackerRepository.save(tracker);
        
        log.info(format:"Applied view - NEW freq={}/{}, NEW cap={}",
            tracker.getRemainingWeeklyFrequency(),
            tracker.getOriginalWeeklyFrequency(),
            tracker.getRemainingDisplayCap());
            
        return true;
    }

  @Transactional
    public CampaignResponseDTO updateEligibleCampaignForRotations(String requestDate, String companyId) {
        throw DateParsingException {
            return getNextEligibleCampaign(requestDate, companyId);
        }
    }

  @Transactional
    public CompanyCampaignTracker getOrCreateTracker(String companyId, CampaignMapping campaign, Date currentDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        log.info(format("Getting/creating tracker for company {}, campaign {} on date {}", 
                companyId, campaign.getId(), sdf.format(currentDate)));
                
        Optional<CompanyCampaignTracker> existingTracker = trackerRepository.findByCompanyIdAndCampaignId(companyId, 
                campaign.getId());
                
        if (existingTracker.isPresent()) {
            CompanyCampaignTracker tracker = existingTracker.get();
            log.info(format("Found existing tracker: freq={}/(), cap={}, lastWeekReset={}", 
                    tracker.getRemainingWeeklyFrequency(),
                    tracker.getOriginalWeeklyFrequency(),
                    tracker.getRemainingDisplayCap(),
                    tracker.getLastWeekReset() != null ? sdf.format(tracker.getLastWeekReset()) : "null"));
                    
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            
            if (tracker.getLastWeekReset() == null || 
                    !sdf.format(rotationUtils.getWeekStartDate(tracker.getLastWeekReset()))
                    .equals(sdf.format(weekStartDate))) {
                log.info(msg:"Tracker needs week reset (different week)");
                
                // Only reset if display cap not exhausted
                if (tracker.getRemainingDisplayCap() == null || tracker.getRemainingDisplayCap() > 0) {
                    log.info(format:"Resetting weekly frequency to original value: {}", 
                            tracker.getOriginalWeeklyFrequency());
                    tracker.setRemainingWeeklyFrequency(tracker.getOriginalWeeklyFrequency());
                    tracker.setLastWeekReset(weekStartDate);
                    trackerRepository.save(tracker);
                } else {
                    log.info(msg:"Not resetting frequency - display cap exhausted");
                }
            } else {
                log.info(msg:"No reset needed - already in current week");
            }
            
            return tracker;
        } else {
            log.info(format:"Creating new tracker for company {}, campaign {}", companyId, campaign.getId());
            
            CompanyCampaignTracker tracker = new CompanyCampaignTracker();
            tracker.setCompanyId(companyId);
            tracker.setCampaignId(campaign.getId());
            tracker.setRemainingWeeklyFrequency(campaign.getFrequencyPerWeek());
            tracker.setOriginalWeeklyFrequency(campaign.getFrequencyPerWeek());
            tracker.setRemainingDisplayCap(campaign.getDisplayCapLimit());
            
            Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
            tracker.setLastUpdated(currentDate);
            tracker.setLastWeekReset(weekStartDate);
            
            log.info(format:"New tracker values: freq={}/(), cap={}, week start={}", 
                    tracker.getRemainingWeeklyFrequency(),
                    tracker.getOriginalWeeklyFrequency(),
                    tracker.getRemainingDisplayCap(),
                    sdf.format(weekStartDate)));
                    
            return trackerRepository.save(tracker);
        }
    }

}
