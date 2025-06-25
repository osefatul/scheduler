package com.ubank.corp.dcr.api.service;

import com.ubank.corp.dcr.api.repository.CampaignLeadRepository;
import com.ubank.corp.dcr.api.entity.CampaignLeadDTO;
import com.ubank.corp.dcr.api.entity.CampaignLeadMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CampaignLeadService {

    private final CampaignLeadRepository campaignLeadRepository;
    private final WarmLeadTrackingService warmLeadTrackingService;

    public CampaignLeadService(CampaignLeadRepository campaignLeadRepository,
                             WarmLeadTrackingService warmLeadTrackingService) {
        this.campaignLeadRepository = campaignLeadRepository;
        this.warmLeadTrackingService = warmLeadTrackingService;
        log.info("CampaignLeadService initialized with repository: {}", campaignLeadRepository);
    }

    @Transactional
    public CampaignLeadDTO createCampaignLead(CampaignLeadDTO campaignLeadDTO) {
        log.info("Creating hot lead (campaign lead) with details: {}", campaignLeadDTO);
        
        // First, try to convert warm lead to hot if it exists
        try {
            if (campaignLeadDTO.getUserIdentifier() != null && 
                campaignLeadDTO.getCampaignId() != null && 
                campaignLeadDTO.getCorporateConnectionInsight() != null) {
                
                warmLeadTrackingService.convertWarmLeadToHot(
                    campaignLeadDTO.getUserIdentifier(),
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
        
        // Build response DTO
        CampaignLeadDTO responseDTO = CampaignLeadDTO.builder()
            .id(saved.getId())
            .leadId(saved.getLeadId())
            .campaignId(saved.getCampaignId())
            .marketingRelationshipType(saved.getMarketingRelationshipType())
            .message("Hot lead created successfully (converted from warm lead)")
            .build();
            
        log.info("Hot lead created successfully with ID: {}", saved.getId());
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
}