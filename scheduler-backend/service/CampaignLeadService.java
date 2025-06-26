package com.usbank.corp.dcr.api.service;

import com.usbank.corp.dcr.api.repository.CampaignLeadRepository;
import com.usbank.corp.dcr.api.entity.CampaignLeadDTO;
import com.usbank.corp.dcr.api.entity.CampaignLeadMapping;
import com.usbank.corp.dcr.api.entity.UserCampaignTracker;
import com.usbank.corp.dcr.api.repository.UserCampaignTrackerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CampaignLeadService {

    private final CampaignLeadRepository campaignLeadRepository;
    private final WarmLeadTrackingService warmLeadTrackingService;
    private final UserCampaignTrackerRepository userCampaignTrackerRepository;

    public CampaignLeadService(CampaignLeadRepository campaignLeadRepository,
                             WarmLeadTrackingService warmLeadTrackingService,
                             UserCampaignTrackerRepository userCampaignTrackerRepository) {
        this.campaignLeadRepository = campaignLeadRepository;
        this.warmLeadTrackingService = warmLeadTrackingService;
        this.userCampaignTrackerRepository = userCampaignTrackerRepository;
        log.info("CampaignLeadService initialized with warm lead tracking and campaign tracker services");
    }

    @Transactional
    public CampaignLeadDTO createCampaignLead(CampaignLeadDTO campaignLeadDTO) {
        log.info("Creating hot lead (campaign lead) with details: {}", campaignLeadDTO);
        
        // Extract user and company identifiers early
        String userIdentifier = extractUserIdentifier(campaignLeadDTO);
        String companyIdentifier = extractCompanyIdentifier(campaignLeadDTO);
        
        log.info("=== HOT LEAD CREATION START ===");
        log.info("User: {}, Company: {}, Campaign: {}", userIdentifier, companyIdentifier, campaignLeadDTO.getCampaignId());
        
        // First, try to convert warm lead to hot if it exists
        try {
            if (userIdentifier != null && 
                campaignLeadDTO.getCampaignId() != null && 
                campaignLeadDTO.getCorporateConnectionInsight() != null) {
                
                warmLeadTrackingService.convertWarmLeadToHot(
                    userIdentifier,
                    campaignLeadDTO.getCampaignId(), 
                    campaignLeadDTO.getCorporateConnectionInsight()
                );
                log.info("Successfully converted warm lead to hot before creating campaign lead");
            }
        } catch (Exception e) {
            log.warn("No warm lead found to convert or conversion failed: {}", e.getMessage());
            // Continue with hot lead creation even if warm lead conversion fails
        }
        
        // Create new campaign lead mapping (hot lead)
        CampaignLeadMapping campaignLeadMapping = new CampaignLeadMapping();
        campaignLeadMapping.setLeadId(campaignLeadDTO.getLeadId());
        campaignLeadMapping.setCampaignId(campaignLeadDTO.getCampaignId());
        campaignLeadMapping.setFirstName(campaignLeadDTO.getFirstName());
        campaignLeadMapping.setLastName(campaignLeadDTO.getLastName());
        campaignLeadMapping.setCompanyName(campaignLeadDTO.getCompanyName());
        campaignLeadMapping.setEmailAddress(campaignLeadDTO.getEmailAddress());
        campaignLeadMapping.setPhoneNumber(campaignLeadDTO.getPhoneNumber());
        campaignLeadMapping.setComments(campaignLeadDTO.getComments());
        campaignLeadMapping.setMarketingRelationshipType(campaignLeadDTO.getMarketingRelationshipType());
        campaignLeadMapping.setCorporateConnectionInsight(campaignLeadDTO.getCorporateConnectionInsight());
        
        // Save the campaign lead mapping
        CampaignLeadMapping saved = campaignLeadRepository.save(campaignLeadMapping);
        log.info("✅ Hot lead created successfully with ID: {}", saved.getId());
        
        // CRITICAL: Exhaust campaign display cap for this user (instead of using closure system)
        boolean exhaustSuccess = exhaustCampaignForUser(
            userIdentifier, 
            companyIdentifier, 
            saved.getCampaignId()
        );
        
        // Build response DTO
        String responseMessage = exhaustSuccess ? 
            "Hot lead created successfully and campaign exhausted for user (can see other campaigns)" :
            "Hot lead created successfully (campaign exhaustion attempted but may have failed - check logs)";
            
        CampaignLeadDTO responseDTO = CampaignLeadDTO.builder()
            .id(saved.getId())
            .leadId(saved.getLeadId())
            .campaignId(saved.getCampaignId())
            .marketingRelationshipType(saved.getMarketingRelationshipType())
            .userIdentifier(userIdentifier)
            .message(responseMessage)
            .build();
            
        log.info("=== HOT LEAD CREATION COMPLETE ===");
        log.info("Hot lead creation process completed. Campaign exhaustion success: {}", exhaustSuccess);
        return responseDTO;
    }

    @Transactional
    public CampaignLeadDTO deleteCampaignLead(CampaignLeadDTO campaignLeadDTO) {
        if (campaignLeadDTO == null || campaignLeadDTO.getId() == null) {
            log.error("CampaignLead or ID is null, cannot delete campaign lead");
            throw new IllegalArgumentException("Lead ID must not be null");
        }

        if (!campaignLeadRepository.existsById(campaignLeadDTO.getId())) {
            log.error("Campaign lead with ID {} does not exist", campaignLeadDTO.getId());
            throw new IllegalArgumentException("Campaign Lead does not exist");
        }

        try {
            log.info("Deleting campaign lead with ID: {}", campaignLeadDTO.getId());
            campaignLeadRepository.deleteById(campaignLeadDTO.getId());
        } catch (Exception e) {
            log.error("Error while deleting campaign lead: {}", e.getMessage());
            throw new RuntimeException("Failed to delete campaign lead", e);
        }

        log.info("Campaign lead with ID {} deleted successfully", campaignLeadDTO.getId());
        return CampaignLeadDTO.builder()
            .id(campaignLeadDTO.getId())
            .message("Campaign Lead Deleted Successfully")
            .build();
    }

    /**
     * Exhaust the campaign for a specific user by setting their display cap to 0
     * This prevents them from seeing this campaign again WITHOUT affecting other campaigns
     * 
     * @param userId User identifier
     * @param companyId Company identifier  
     * @param campaignId Campaign identifier
     * @return true if exhaustion was successful, false otherwise
     */
    private boolean exhaustCampaignForUser(String userId, String companyId, String campaignId) {
        log.info("=== CAMPAIGN EXHAUSTION START ===");
        log.info("Exhausting campaign {} for user {} in company {} (HOT LEAD CONVERSION)", campaignId, userId, companyId);
        
        try {
            Date currentDate = new Date();
            
            // Find all trackers for this user-campaign combination
            List<UserCampaignTracker> existingTrackers = userCampaignTrackerRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
            
            if (existingTrackers.isEmpty()) {
                log.info("No existing trackers found for user {}, campaign {}. Creating exhausted tracker.", userId, campaignId);
                
                // Create a new tracker with exhausted values
                UserCampaignTracker exhaustedTracker = new UserCampaignTracker();
                exhaustedTracker.setId(java.util.UUID.randomUUID().toString());
                exhaustedTracker.setUserId(userId);
                exhaustedTracker.setCompanyId(companyId);
                exhaustedTracker.setCampaignId(campaignId);
                
                // Set to 0 to indicate exhausted (no more views available)
                exhaustedTracker.setRemainingWeeklyFrequency(0);
                exhaustedTracker.setRemainingDisplayCap(0);
                
                // Set current date and week start
                exhaustedTracker.setLastViewDate(currentDate);
                
                // Calculate current week start date (you might need to inject RotationUtils here)
                Date weekStartDate = getCurrentWeekStartDate(currentDate);
                exhaustedTracker.setWeekStartDate(weekStartDate);
                
                UserCampaignTracker saved = userCampaignTrackerRepository.save(exhaustedTracker);
                
                log.info("✅ Created exhausted tracker for user {}, campaign {} with ID: {}", 
                        userId, campaignId, saved.getId());
                log.info("  - Weekly Frequency: {} (exhausted)", saved.getRemainingWeeklyFrequency());
                log.info("  - Display Cap: {} (exhausted)", saved.getRemainingDisplayCap());
                
            } else {
                log.info("Found {} existing tracker(s) for user {}, campaign {}. Exhausting all.", 
                        existingTrackers.size(), userId, campaignId);
                
                // Exhaust all existing trackers for this user-campaign combination
                for (UserCampaignTracker tracker : existingTrackers) {
                    log.info("Exhausting tracker ID: {}, Current freq: {}, Current cap: {}", 
                            tracker.getId(), tracker.getRemainingWeeklyFrequency(), tracker.getRemainingDisplayCap());
                    
                    // Set both counters to 0 (exhausted)
                    tracker.setRemainingWeeklyFrequency(0);
                    tracker.setRemainingDisplayCap(0);
                    tracker.setLastViewDate(currentDate);
                    
                    UserCampaignTracker updated = userCampaignTrackerRepository.save(tracker);
                    
                    log.info("✅ Exhausted tracker ID: {}, New freq: {}, New cap: {}", 
                            updated.getId(), updated.getRemainingWeeklyFrequency(), updated.getRemainingDisplayCap());
                }
            }
            
            log.info("=== CAMPAIGN EXHAUSTION COMPLETE ===");
            log.info("Campaign {} successfully exhausted for user {}. User can still see OTHER campaigns.", campaignId, userId);
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to exhaust campaign {} for user {}: {}", campaignId, userId, e.getMessage(), e);
            log.info("=== CAMPAIGN EXHAUSTION FAILED ===");
            return false;
        }
    }
    
    /**
     * Get the start date of the current week (Monday)
     * This is a simplified version - you might want to inject RotationUtils for this
     */
    private Date getCurrentWeekStartDate(Date currentDate) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(currentDate);
        
        // Set to Monday of current week
        cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        
        return cal.getTime();
    }
    
    /**
     * Extract user identifier from the campaign lead DTO
     * Uses userIdentifier field, falls back to email prefix if not available
     */
    private String extractUserIdentifier(CampaignLeadDTO dto) {
        if (dto.getUserIdentifier() != null && !dto.getUserIdentifier().trim().isEmpty()) {
            return dto.getUserIdentifier().trim();
        }
        
        // Fallback: use email prefix as user identifier
        if (dto.getEmailAddress() != null && dto.getEmailAddress().contains("@")) {
            String emailPrefix = dto.getEmailAddress().split("@")[0];
            log.info("No userIdentifier provided, using email prefix as user identifier: {}", emailPrefix);
            return emailPrefix;
        }
        
        // Last resort: use first name + last name
        if (dto.getFirstName() != null && dto.getLastName() != null) {
            String nameIdentifier = (dto.getFirstName() + "." + dto.getLastName()).toLowerCase();
            log.info("No email available, using name as user identifier: {}", nameIdentifier);
            return nameIdentifier;
        }
        
        log.warn("Could not extract user identifier from campaign lead DTO");
        return "UNKNOWN_USER";
    }
    
    /**
     * Extract company identifier from the campaign lead DTO
     * Uses company name, falls back to email domain if not available
     */
    private String extractCompanyIdentifier(CampaignLeadDTO dto) {
        if (dto.getCompanyName() != null && !dto.getCompanyName().trim().isEmpty()) {
            return dto.getCompanyName().trim().toUpperCase();
        }
        
        // Fallback: use email domain as company identifier
        if (dto.getEmailAddress() != null && dto.getEmailAddress().contains("@")) {
            String emailDomain = dto.getEmailAddress().split("@")[1];
            String companyFromDomain = emailDomain.replace(".", "").toUpperCase();
            log.info("No company name provided, using email domain as company identifier: {}", companyFromDomain);
            return companyFromDomain;
        }
        
        log.warn("Could not extract company identifier from campaign lead DTO");
        return "UNKNOWN_COMPANY";
    }
}