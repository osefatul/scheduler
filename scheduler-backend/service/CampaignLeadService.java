package com.usbank.corp.dcr.api.service;

import com.usbank.corp.dcr.api.repository.CampaignLeadRepository;
import com.usbank.corp.dcr.api.entity.CampaignLeadDTO;
import com.usbank.corp.dcr.api.entity.CampaignLeadMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CampaignLeadService {

    private final CampaignLeadRepository campaignLeadRepository;
    private final WarmLeadTrackingService warmLeadTrackingService;
    private final CampaignExhaustionService campaignExhaustionService;

    public CampaignLeadService(CampaignLeadRepository campaignLeadRepository,
                             WarmLeadTrackingService warmLeadTrackingService,
                             CampaignExhaustionService campaignExhaustionService) {
        this.campaignLeadRepository = campaignLeadRepository;
        this.warmLeadTrackingService = warmLeadTrackingService;
        this.campaignExhaustionService = campaignExhaustionService;
        log.info("CampaignLeadService initialized with warm lead tracking and campaign exhaustion services");
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
        
        // CRITICAL: Exhaust this specific campaign for this user only
        boolean exhaustionSuccess = campaignExhaustionService.exhaustCampaignForUser(
            userIdentifier, 
            companyIdentifier, 
            saved.getCampaignId()
        );
        
        // Build response DTO
        String responseMessage = exhaustionSuccess ? 
            "Hot lead created and campaign exhausted - user will not see this campaign again but can see others" :
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
        log.info("Hot lead creation process completed. Campaign exhaustion success: {}", exhaustionSuccess);
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
     * Extract user identifier from the campaign lead DTO
     */
    private String extractUserIdentifier(CampaignLeadDTO dto) {
        if (dto.getUserIdentifier() != null && !dto.getUserIdentifier().trim().isEmpty()) {
            return dto.getUserIdentifier().trim();
        }
        
        if (dto.getEmailAddress() != null && dto.getEmailAddress().contains("@")) {
            String emailPrefix = dto.getEmailAddress().split("@")[0];
            log.info("No userIdentifier provided, using email prefix: {}", emailPrefix);
            return emailPrefix;
        }
        
        if (dto.getFirstName() != null && dto.getLastName() != null) {
            String nameIdentifier = (dto.getFirstName() + "." + dto.getLastName()).toLowerCase();
            log.info("Using name as user identifier: {}", nameIdentifier);
            return nameIdentifier;
        }
        
        log.warn("Could not extract user identifier from campaign lead DTO");
        return "UNKNOWN_USER";
    }
    
    /**
     * Extract company identifier from the campaign lead DTO
     */
    private String extractCompanyIdentifier(CampaignLeadDTO dto) {
        if (dto.getCompanyName() != null && !dto.getCompanyName().trim().isEmpty()) {
            return dto.getCompanyName().trim().toUpperCase();
        }
        
        if (dto.getEmailAddress() != null && dto.getEmailAddress().contains("@")) {
            String emailDomain = dto.getEmailAddress().split("@")[1];
            String companyFromDomain = emailDomain.replace(".", "").toUpperCase();
            log.info("Using email domain as company identifier: {}", companyFromDomain);
            return companyFromDomain;
        }
        
        log.warn("Could not extract company identifier from campaign lead DTO");
        return "UNKNOWN_COMPANY";
    }
}