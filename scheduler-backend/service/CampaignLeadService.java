package com.usbank.corp.dcr.api.service;

import com.usbank.corp.dcr.api.repository.CampaignLeadRepository;
import com.usbank.corp.dcr.api.entity.CampaignLeadDTO;
import com.usbank.corp.dcr.api.entity.CampaignLeadMapping;
import com.usbank.corp.dcr.api.entity.UserInsightClosure;
import com.usbank.corp.dcr.api.repository.UserInsightClosureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class CampaignLeadService {

    private final CampaignLeadRepository campaignLeadRepository;
    private final WarmLeadTrackingService warmLeadTrackingService;
    private final UserInsightClosureService userInsightClosureService;
    private final UserInsightClosureRepository closureRepository;

    public CampaignLeadService(CampaignLeadRepository campaignLeadRepository,
                             WarmLeadTrackingService warmLeadTrackingService,
                             UserInsightClosureService userInsightClosureService,
                             UserInsightClosureRepository closureRepository) {
        this.campaignLeadRepository = campaignLeadRepository;
        this.warmLeadTrackingService = warmLeadTrackingService;
        this.userInsightClosureService = userInsightClosureService;
        this.closureRepository = closureRepository;
        log.info("CampaignLeadService initialized with warm lead tracking and closure services");
    }

    @Transactional
    public CampaignLeadDTO createCampaignLead(CampaignLeadDTO campaignLeadDTO) {
        log.info("Creating hot lead (campaign lead) with details: {}", campaignLeadDTO);
        
        // Extract user and company identifiers early for closure logic
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
        
        // CRITICAL: Permanently close the campaign for this user after successful hot lead creation
        boolean closureSuccess = permanentlyCloseCampaignForUser(
            userIdentifier, 
            companyIdentifier, 
            saved.getCampaignId(), 
            "CONVERTED_TO_HOT_LEAD - User submitted form and became a hot lead"
        );
        
        // Build response DTO
        String responseMessage = closureSuccess ? 
            "Hot lead created successfully and campaign permanently closed" :
            "Hot lead created successfully (campaign closure attempted but may have failed - check logs)";
            
        CampaignLeadDTO responseDTO = CampaignLeadDTO.builder()
            .id(saved.getId())
            .leadId(saved.getLeadId())
            .campaignId(saved.getCampaignId())
            .marketingRelationshipType(saved.getMarketingRelationshipType())
            .userIdentifier(userIdentifier)
            .message(responseMessage)
            .build();
            
        log.info("=== HOT LEAD CREATION COMPLETE ===");
        log.info("Hot lead creation process completed. Closure success: {}", closureSuccess);
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
     * Permanently close a campaign for a user after they become a hot lead
     * This ensures the user will NEVER see this campaign/banner again
     * 
     * @param userId User identifier
     * @param companyId Company identifier  
     * @param campaignId Campaign identifier
     * @param reason Reason for closure
     * @return true if closure was successful, false otherwise
     */
    private boolean permanentlyCloseCampaignForUser(String userId, String companyId, String campaignId, String reason) {
        log.info("=== PERMANENT CAMPAIGN CLOSURE START ===");
        log.info("Permanently closing campaign {} for user {} in company {}", campaignId, userId, companyId);
        log.info("Reason: {}", reason);
        
        try {
            Date effectiveDate = new Date();
            
            // METHOD 1: Try using the existing service method (preferred)
            try {
                log.info("Attempting closure using UserInsightClosureService...");
                
                // First record a closure to ensure the closure record exists
                userInsightClosureService.recordInsightClosure(userId, companyId, campaignId, effectiveDate);
                
                // Then handle it as "Don't show again" response 
                userInsightClosureService.handlePreferenceResponse(
                    userId, 
                    companyId, 
                    campaignId, 
                    false, // wantsToSee = false (equivalent to "Don't show again")
                    reason, 
                    false, // isGlobalResponse = false (campaign-specific permanent block)
                    effectiveDate
                );
                
                log.info("✅ Successfully permanently closed campaign {} for user {} using UserInsightClosureService", 
                        campaignId, userId);
                
                // Verify the closure was applied correctly
                boolean isNowClosed = userInsightClosureService.isCampaignClosedForUser(userId, companyId, campaignId, effectiveDate);
                log.info("Verification: Campaign {} is now closed for user {}: {}", campaignId, userId, isNowClosed);
                
                return true;
                
            } catch (Exception serviceException) {
                log.warn("UserInsightClosureService method failed: {}. Trying direct database approach...", serviceException.getMessage());
                
                // METHOD 2: Direct database manipulation as fallback
                return createPermanentClosureRecordDirect(userId, companyId, campaignId, reason, effectiveDate);
            }
            
        } catch (Exception e) {
            log.error("❌ Complete failure in permanent campaign closure: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Fallback method: Create permanent closure record directly in database
     * This ensures the campaign is blocked even if the service method fails
     */
    private boolean createPermanentClosureRecordDirect(String userId, String companyId, String campaignId, String reason, Date effectiveDate) {
        try {
            log.info("Creating permanent closure record directly in database...");
            
            // Find existing closure record or create new one
            Optional<UserInsightClosure> existingClosureOpt = closureRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
            
            UserInsightClosure closure;
            if (existingClosureOpt.isPresent()) {
                closure = existingClosureOpt.get();
                log.info("Found existing closure record with count: {}", closure.getClosureCount());
            } else {
                closure = new UserInsightClosure();
                closure.setId(UUID.randomUUID().toString());
                closure.setUserId(userId);
                closure.setCompanyId(companyId);
                closure.setCampaignId(campaignId);
                closure.setClosureCount(0);
                closure.setCreatedDate(effectiveDate);
                log.info("Created new closure record");
            }
            
            // Set permanent closure flags
            closure.setPermanentlyClosed(true);
            closure.setClosureReason(reason);
            closure.setLastClosureDate(effectiveDate);
            closure.setUpdatedDate(effectiveDate);
            
            // Set next eligible date far in the future (10+ years = effectively permanent)
            Calendar cal = Calendar.getInstance();
            cal.setTime(effectiveDate);
            cal.add(Calendar.YEAR, 10);
            closure.setNextEligibleDate(cal.getTime());
            
            // Ensure opt-out flag is false (this is campaign-specific, not global opt-out)
            closure.setOptOutAllInsights(false);
            
            // Save the closure record
            UserInsightClosure savedClosure = closureRepository.save(closure);
            
            log.info("✅ Direct database closure record created successfully:");
            log.info("  - Campaign ID: {}", savedClosure.getCampaignId());
            log.info("  - Permanently Closed: {}", savedClosure.getPermanentlyClosed());
            log.info("  - Next Eligible Date: {}", savedClosure.getNextEligibleDate());
            log.info("  - Closure Reason: {}", savedClosure.getClosureReason());
            
            // Verify using the service method
            boolean isNowClosed = userInsightClosureService.isCampaignClosedForUser(userId, companyId, campaignId, effectiveDate);
            log.info("Verification: Campaign {} is now closed for user {}: {}", campaignId, userId, isNowClosed);
            
            log.info("=== PERMANENT CAMPAIGN CLOSURE COMPLETE (DIRECT) ===");
            return true;
            
        } catch (Exception e) {
            log.error("❌ Direct database closure also failed: {}", e.getMessage(), e);
            log.info("=== PERMANENT CAMPAIGN CLOSURE FAILED ===");
            return false;
        }
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