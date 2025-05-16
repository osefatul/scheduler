package com.usbank.corp.dcr.api.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usbank.corp.dcr.api.entity.CampaignCompanyMapping;
import com.usbank.corp.dcr.api.repository.CampaignCompanyRepository;

import com.usbank.corp.dcr.api.repository.RHTestEmailIdRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CampaignCompanyService {

    private final CampaignCompanyRepository campaignCompanyRepository;
    private final InsightService insightService;
    private final CampaignRepository campaignRepository;
    private final RHTestEmailIdRepository testEmailIdRepository;

    public List<CampaignCompanyMapping> getCampaignsByRHId(String rhemailId) {
        List<CampaignCompanyMapping> allCampaignCompanyMapping=campaignCompanyRepository.findByRhemailId(rhemailId);
        return allCampaignCompanyMapping;
    }
    
    public CampaignCompanyMapping getCampaignCompanyMapping(String campaignId) {
        List<CampaignCompanyMapping> campaignCompanyMapping=campaignCompanyRepository.findByCampaignId(campaignId);
        return campaignCompanyMapping.stream().findFirst().get();
    }
    
    @Autowired
    public CampaignCompanyService(CampaignCompanyRepository campaignCompanyRepository, InsightService insightService,
            CampaignRepository campaignRepository,RHTestEmailIdRepository testEmailIdRepository) {
        this.campaignCompanyRepository = campaignCompanyRepository;
        this.insightService = insightService;
        this.campaignRepository = campaignRepository;
        this.testEmailIdRepository=testEmailIdRepository;
    }
    
    // @Transactional
    public void associateCampaignWithCompanies(String campaignId, List<String> companyIds, String insightType,
            String insightSubType, String insight, String flowType) {
        log.info("Associating campaign {} with companies: {}", campaignId, companyIds);
        try {
            InsightDataMapping insightDataMapping = null;
            if (flowType == "update") {
                Optional<CampaignMapping> configuredCampaign = campaignRepository.findById(campaignId);
                if (configuredCampaign.isPresent()) {
                    insightType = configuredCampaign.get().getInsightType();
                    insightSubType = configuredCampaign.get().getInsightSubType();
                    insight = configuredCampaign.get().getInsight();
                }
            }
            Optional<InsightDataMapping> insightListConfigured = insightService
                .getInsightsByFilters(insightType, insightSubType, insight).stream().findFirst();
            insightDataMapping = insightListConfigured.isPresent() ? insightListConfigured.get() : null;
            if (insightDataMapping == null) {
                throw new RuntimeException("No insights configured for the given filters");
            }
            // First, get existing mappings
            List<CampaignCompanyMapping> dbCompanyIds = campaignCompanyRepository.findByCampaignId(campaignId);
            
            // Extract the existing company IDs
            Set<String> existingCompanyIds = dbCompanyIds.stream().map(CampaignCompanyMapping::getCompanyId)
                .collect(Collectors.toSet());
            
            // Use a Set to ensure we don't have duplicate companies
            Set<String> passedCompanyIds = new HashSet<>(companyIds);
            
            // Remove mappings for companies that are no longer in the list
            for (CampaignCompanyMapping mapping : dbCompanyIds) {
                if (!passedCompanyIds.contains(mapping.getCompanyId())) {
                    campaignCompanyRepository.delete(mapping);
                }
            }
            
            // Add mappings for companies that are not already mapped
            List<CampaignCompanyMapping> newMappings = new ArrayList<>();
            for (String companyId : passedCompanyIds) {
                if (!existingCompanyIds.contains(companyId)) {
                    CampaignCompanyMapping mapping = new CampaignCompanyMapping();
                    mapping.setId(UUID.randomUUID().toString());
                    mapping.setCampaignId(campaignId);
                    mapping.setCompanyId(companyId);
                    mapping.setRhName(insightDataMapping.getRheNRName());
                    mapping.setRhEmailId(insightDataMapping.getRheNREmailId());
                    mapping.setRhIntranetId(insightDataMapping.getRheNRIntranetId());
                    newMappings.add(mapping);
                }
            }
            
            // Save all new mappings
            if (!newMappings.isEmpty()) {
                campaignCompanyRepository.saveAll(newMappings);
            }
            
            log.info("Successfully updated associations for campaign {}", campaignId);
        } catch (Exception e) {
            log.error("Error associating campaign {} with companies: {}", campaignId, e.getMessage(), e);
            throw e;
        }
    }
    
    public List<String> getCompaniesForCampaign(String campaignId) {
        return campaignCompanyRepository.findByCampaignId(campaignId).stream().map(CampaignCompanyMapping::getCompanyId)
            .collect(Collectors.toList());
    }
    
    public String getRHTestEmailIdForCampaign(String env) {
        return testEmailIdRepository.findByEnv(env).getRhTestEmailId();
    }
    
    public List<String> getCampaignsForCompany(String companyId) {
        return campaignCompanyRepository.findByCompanyId(companyId).stream().map(CampaignCompanyMapping::getCampaignId)
            .collect(Collectors.toList());
    }
    
    public boolean isCampaignAssociatedWithCompany(String campaignId, String companyId) {
        return campaignCompanyRepository.existsByCampaignIdAndCompanyId(campaignId, companyId);
    }
    
    public List<String> processCompanyNames(String companyNames) {
        if (companyNames == null || companyNames.isEmpty()) {
            return new ArrayList<>();
        }
        
        return List.of(companyNames.split("\\|\\|"));
    }
}