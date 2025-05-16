package controller;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usbank.corp.dcr.api.model.CampaignRequestDTO;
import com.usbank.corp.dcr.api.model.CampaignResponseDTO;
import com.usbank.corp.dcr.api.service.CampaignCompanyService;
import com.usbank.corp.dcr.api.service.CampaignService;

@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignCompanyService campaignCompanyService;

    @Autowired
    public CampaignController(CampaignService campaignService, 
                             CampaignCompanyService campaignCompanyService) {
        this.campaignService = campaignService;
        this.campaignCompanyService = campaignCompanyService;
    }

    @PostMapping
    public ResponseEntity<CampaignResponseDTO> createCampaign(@Valid @RequestBody CampaignRequestDTO request) {
        // Create the campaign
        CampaignResponseDTO response = campaignService.createOrUpdateCampaign(request);
        
        // Process company mappings
        if (request.getCompanyNames() != null && !request.getCompanyNames().isEmpty()) {
            List<String> companyIds = campaignCompanyService.processCompanyNames(request.getCompanyNames());
            campaignCompanyService.associateCampaignWithCompanies(response.getId(), companyIds);
            
            // Update response with company names
            response.setCompanyNames(request.getCompanyNames());
        }
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping
    public ResponseEntity<List<CampaignResponseDTO>> getAllCampaigns() {
        List<CampaignResponseDTO> campaigns = campaignService.getAllCampaigns();
        
        // Add company information to each campaign
        for (CampaignResponseDTO campaign : campaigns) {
            List<String> companies = campaignCompanyService.getCompaniesForCampaign(campaign.getId());
            campaign.setCompanyNames(String.join("|", companies));
        }
        
        return ResponseEntity.ok(campaigns);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponseDTO> getCampaignById(@PathVariable String id) {
        CampaignResponseDTO campaign = campaignService.getCampaignById(id);
        
        // Add company information
        List<String> companies = campaignCompanyService.getCompaniesForCampaign(campaign.getId());
        campaign.setCompanyNames(String.join("|", companies));
        
        return ResponseEntity.ok(campaign);
    }

    @GetMapping("/user/{createdBy}")
    public ResponseEntity<List<CampaignResponseDTO>> getCampaignsByCreatedBy(@PathVariable String createdBy) {
        List<CampaignResponseDTO> campaigns = campaignService.getCampaignsByCreatedBy(createdBy);
        
        // Add company information to each campaign
        for (CampaignResponseDTO campaign : campaigns) {
            List<String> companies = campaignCompanyService.getCompaniesForCampaign(campaign.getId());
            campaign.setCompanyNames(String.join("|", companies));
        }
        
        return ResponseEntity.ok(campaigns);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<CampaignResponseDTO>> getCampaignsByStatus(@PathVariable String status) {
        List<CampaignResponseDTO> campaigns = campaignService.getCampaignsByStatus(status);
        
        // Add company information to each campaign
        for (CampaignResponseDTO campaign : campaigns) {
            List<String> companies = campaignCompanyService.getCompaniesForCampaign(campaign.getId());
            campaign.setCompanyNames(String.join("|", companies));
        }
        
        return ResponseEntity.ok(campaigns);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<CampaignResponseDTO> updateCampaignStatus(
            @PathVariable String id,
            @RequestParam String status) {
        CampaignResponseDTO updatedCampaign = campaignService.updateCampaignStatus(id, status);
        
        // Add company information
        List<String> companies = campaignCompanyService.getCompaniesForCampaign(updatedCampaign.getId());
        updatedCampaign.setCompanyNames(String.join("|", companies));
        
        return ResponseEntity.ok(updatedCampaign);
    }
    
    @PutMapping("/{id}/companies")
    public ResponseEntity<CampaignResponseDTO> updateCampaignCompanies(
            @PathVariable String id,
            @RequestParam String companyNames) {
        
        // Update campaign company associations
        List<String> companyIds = campaignCompanyService.processCompanyNames(companyNames);
        campaignCompanyService.associateCampaignWithCompanies(id, companyIds);
        
        // Get updated campaign
        CampaignResponseDTO campaign = campaignService.getCampaignById(id);
        campaign.setCompanyNames(companyNames);
        
        return ResponseEntity.ok(campaign);
    }
}