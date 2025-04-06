package com.usbank.corp.dcr.api.service;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usbank.corp.dcr.api.entity.CampaignMapping;
import com.usbank.corp.dcr.api.model.CampaignRequestDTO;
import com.usbank.corp.dcr.api.model.CampaignResponseDTO;
import com.usbank.corp.dcr.api.repository.CampaignRepository;
import com.usbank.corp.dcr.api.utils.ContentUtils;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignCompanyService campaignCompanyService;
    
    private static final String REVIEW = "REVIEW";
    private static final String INPROGRESS = "INPROGRESS";
    private static final String DRAFTED = "DRAFT";
    
    @Autowired
    public CampaignService(CampaignRepository campaignRepository,
                          CampaignCompanyService campaignCompanyService) {
        this.campaignRepository = campaignRepository;
        this.campaignCompanyService = campaignCompanyService;
    }

    /**
     * Creates or updates a campaign based on the multi-step form process
     * 
     * @param request Campaign request containing form data
     * @return Response DTO with campaign data
     */
    @Transactional
    public CampaignResponseDTO createOrUpdateCampaign(CampaignRequestDTO request) {
        // Check if this is an update to an existing campaign
        CampaignResponseDTO campaignResponseDTO = null;
        if (Objects.nonNull(request.getId()) && !request.getId().isEmpty()) {
            campaignResponseDTO = updateCampaignStatusAndCampaignSteps(request.getId(), request.getAction());
        }
        
        // If no existing campaign or update failed, create a new one
        if (campaignResponseDTO == null) {
            // Create new campaign
            CampaignMapping campaign = new CampaignMapping();
            
            // Set basic properties
            campaign.setName(request.getName());
            campaign.setBannerId(request.getBannerId());
            campaign.setInsightType(request.getInsightType());
            campaign.setInsightSubType(request.getInsightSubType());
            campaign.setInsight(request.getInsight());
            campaign.setEligibleCompanies(request.getEligibleCompanies());
            campaign.setEligibleUsers(request.getEligibleUsers());
            
            // Set dates if provided
            if (request.getStartDate() != null) {
                campaign.setStartDate(request.getStartDate());
            }
            if (request.getEndDate() != null) {
                campaign.setEndDate(request.getEndDate());
            }
            
            // Set other properties if provided
            if (request.getFrequencyPerWeek() != null) {
                campaign.setFrequencyPerWeek(request.getFrequencyPerWeek());
            }
            if (request.getDisplayCapping() != null) {
                campaign.setDisplayCapping(request.getDisplayCapping());
            }
            if (request.getDisplayLocation() != null) {
                campaign.setDisplayLocation(request.getDisplayLocation());
            }
            
            campaign.setCreatedBy(request.getCreatedBy());
            campaign.setCreatedDate(new Date());
            
            // Set status and campaign steps based on action
            String[] statusAndSteps = getStatusAndStepsFromAction(request.getAction());
            campaign.setStatus(statusAndSteps[0]);
            campaign.setCampaignSteps(statusAndSteps[1]);
            
            // Save campaign
            CampaignMapping savedCampaign = campaignRepository.save(campaign);
            
            // If company names are provided, associate them
            if (request.getCompanyNames() != null && !request.getCompanyNames().isEmpty()) {
                List<String> companyIds = campaignCompanyService.processCompanyNames(request.getCompanyNames());
                campaignCompanyService.associateCampaignWithCompanies(savedCampaign.getId(), companyIds);
            }
            
            campaignResponseDTO = mapToDTO(savedCampaign);
            
            // Set company names in response
            if (request.getCompanyNames() != null) {
                campaignResponseDTO.setCompanyNames(request.getCompanyNames());
            }
        }
        
        return campaignResponseDTO;
    }
    
    /**
     * Determine the status and campaign steps based on the action
     * 
     * @param action Action from the form (SETUP, CONFIGURE, REVIEW, SUBMIT)
     * @return Array with [status, campaignSteps]
     */
    private String[] getStatusAndStepsFromAction(String action) {
        String status = DRAFTED;
        String campaignSteps = "";
        
        if (action == null) {
            return new String[]{status, campaignSteps};
        }
        
        switch (action.toUpperCase()) {
            case "SETUP":
                campaignSteps = "MAP_INSIGHT";
                break;
            case "CONFIGURE":
                campaignSteps = "CAMPAIGN_DURATION";
                break;
            case "REVIEW":
                campaignSteps = REVIEW;
                break;
            case "SUBMIT":
                status = INPROGRESS;
                campaignSteps = "SUBMIT";
                break;
            default:
                // Default to DRAFT status if unknown action
                campaignSteps = "MAP_INSIGHT";
                break;
        }
        
        return new String[]{status, campaignSteps};
    }

    /**
     * Get all campaigns
     * 
     * @return List of campaign response DTOs
     */
    public List<CampaignResponseDTO> getAllCampaigns() {
        List<CampaignMapping> campaigns = campaignRepository.findAll();
        
        return campaigns.stream()
                .map(this::mapToDTOWithCompanies)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all eligible campaigns for rotation
     * 
     * @return List of campaign response DTOs
     */
    public List<CampaignResponseDTO> getEligibleCampaignsForRotations() {
        List<CampaignMapping> campaigns = campaignRepository.getEligibleCampaignsForRotations();
        
        return campaigns.stream()
                .map(this::mapToDTOWithCompanies)
                .collect(Collectors.toList());
    }
    
    /**
     * Get campaign by ID
     * 
     * @param id Campaign ID
     * @return Campaign response DTO
     */
    public CampaignResponseDTO getCampaignById(String id) {
        CampaignMapping campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + id));
        
        return mapToDTOWithCompanies(campaign);
    }
    
    /**
     * Get campaigns by created by
     * 
     * @param createdBy Creator identifier
     * @return List of campaign response DTOs
     */
    public List<CampaignResponseDTO> getCampaignsByCreatedBy(String createdBy) {
        List<CampaignMapping> campaigns = campaignRepository.findByCreatedBy(createdBy);
        
        return campaigns.stream()
                .map(this::mapToDTOWithCompanies)
                .collect(Collectors.toList());
    }
    
    /**
     * Get campaigns by status
     * 
     * @param status Status to filter by
     * @return List of campaign response DTOs
     */
    public List<CampaignResponseDTO> getCampaignsByStatus(String status) {
        List<CampaignMapping> campaigns = campaignRepository.findByStatus(status);
        
        return campaigns.stream()
                .map(this::mapToDTOWithCompanies)
                .collect(Collectors.toList());
    }
    
    /**
     * Update campaign status
     * 
     * @param id Campaign ID
     * @param status New status
     * @return Updated campaign response DTO
     */
    public CampaignResponseDTO updateCampaignStatus(String id, String status) {
        CampaignMapping campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + id));
        
        campaign.setStatus(status);
        CampaignMapping updatedCampaign = campaignRepository.save(campaign);
        
        return mapToDTOWithCompanies(updatedCampaign);
    }
    
    /**
     * Update campaign status and steps based on the action
     * 
     * @param id Campaign ID
     * @param action Action (SETUP, CONFIGURE, REVIEW, SUBMIT)
     * @return Updated campaign response DTO
     */
    public CampaignResponseDTO updateCampaignStatusAndCampaignSteps(String id, String action) {
        CampaignMapping campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + id));
        
        String[] statusAndSteps = getStatusAndStepsFromAction(action);
        campaign.setStatus(statusAndSteps[0]);
        campaign.setCampaignSteps(statusAndSteps[1]);
        
        CampaignMapping updatedCampaign = campaignRepository.save(campaign);
        
        return mapToDTOWithCompanies(updatedCampaign);
    }
    
    /**
     * Map campaign entity to DTO
     * 
     * @param campaign Campaign entity
     * @return Campaign response DTO
     */
    public CampaignResponseDTO mapToDTO(CampaignMapping campaign) {
        CampaignResponseDTO response = new CampaignResponseDTO();
        response.setId(campaign.getId());
        response.setName(campaign.getName());
        response.setBannerId(campaign.getBannerId());
        response.setInsightType(campaign.getInsightType());
        response.setInsightSubType(campaign.getInsightSubType());
        response.setInsight(campaign.getInsight());
        response.setEligibleCompanies(campaign.getEligibleCompanies());
        response.setEligibleUsers(campaign.getEligibleUsers());
        response.setStartDate(campaign.getStartDate());
        response.setEndDate(campaign.getEndDate());
        response.setFrequencyPerWeek(campaign.getFrequencyPerWeek());
        response.setDisplayCapping(campaign.getDisplayCapping());
        response.setDisplayLocation(campaign.getDisplayLocation());
        response.setCreatedBy(campaign.getCreatedBy());
        response.setCreatedDate(campaign.getCreatedDate());
        response.setStatus(campaign.getStatus());
        response.setVisibility(campaign.getVisibility());
        response.setCampaignSteps(campaign.getCampaignSteps());
        return response;
    }
    
    /**
     * Map campaign entity to DTO including company information
     * 
     * @param campaign Campaign entity
     * @return Campaign response DTO
     */
    public CampaignResponseDTO mapToDTOWithCompanies(CampaignMapping campaign) {
        CampaignResponseDTO response = mapToDTO(campaign);
        
        // Get associated companies
        List<String> companies = campaignCompanyService.getCompaniesForCampaign(campaign.getId());
        response.setCompanyNames(String.join("|", companies));
        
        return response;
    }
}