package com.usbank.corp.dcr.api.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usbank.corp.dcr.api.entity.CampaignCompanyMapping;
import com.usbank.corp.dcr.api.repository.CampaignCompanyRepository;

/**
 * Service for managing the campaign to company mappings
 */
@Service
public class CampaignCompanyService {
    
    private final CampaignCompanyRepository campaignCompanyRepository;
    private final Logger log = LoggerFactory.getLogger(CampaignCompanyService.class);
    
    @Autowired
    public CampaignCompanyService(CampaignCompanyRepository campaignCompanyRepository) {
        this.campaignCompanyRepository = campaignCompanyRepository;
    }
    
    /**
     * Associate a campaign with multiple companies
     * This is now a new, separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void associateCampaignWithCompanies(String campaignId, List<String> companyIds) {
        log.info("Associating campaign {} with companies: {}", campaignId, companyIds);
        
        try {
            // Delete any existing mappings first
            campaignCompanyRepository.deleteByCampaignId(campaignId);
            
            // Use a Set to ensure we don't have duplicate companies
            Set<String> uniqueCompanyIds = new HashSet<>(companyIds);
            
            List<CampaignCompanyMapping> mappings = new ArrayList<>();
            
            for (String companyId : uniqueCompanyIds) {
                CampaignCompanyMapping mapping = new CampaignCompanyMapping();
                mapping.setId(UUID.randomUUID().toString()); // Generate a unique ID
                mapping.setCampaignId(campaignId);
                mapping.setCompanyId(companyId);
                mappings.add(mapping);
            }
            
            // Save all at once
            campaignCompanyRepository.saveAll(mappings);
            
            log.info("Successfully associated campaign {} with {} companies", campaignId, mappings.size());
        } catch (Exception e) {
            log.error("Error associating campaign {} with companies: {}", campaignId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get all companies associated with a campaign
     * 
     * @param campaignId Campaign ID
     * @return List of company IDs
     */
    public List<String> getCompaniesForCampaign(String campaignId) {
        return campaignCompanyRepository.findByCampaignId(campaignId)
                .stream()
                .map(CampaignCompanyMapping::getCompanyId)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all campaigns associated with a company
     * 
     * @param companyId Company ID
     * @return List of campaign IDs
     */
    public List<String> getCampaignsForCompany(String companyId) {
        return campaignCompanyRepository.findByCompanyId(companyId)
                .stream()
                .map(CampaignCompanyMapping::getCampaignId)
                .collect(Collectors.toList());
    }
    
    /**
     * Check if a campaign is associated with a company
     * 
     * @param campaignId Campaign ID
     * @param companyId Company ID
     * @return true if associated, false otherwise
     */
    public boolean isCampaignAssociatedWithCompany(String campaignId, String companyId) {
        return campaignCompanyRepository.existsByCampaignIdAndCompanyId(campaignId, companyId);
    }
    
    /**
     * Process a pipe-separated string of company IDs
     * 
     * @param companyNames Pipe-separated string (e.g., "Company1|Company2|Company3")
     * @return List of company IDs
     */
    public List<String> processCompanyNames(String companyNames) {
        if (companyNames == null || companyNames.isEmpty()) {
            return new ArrayList<>();
        }
        
        return List.of(companyNames.split("\\|"));
    }
}