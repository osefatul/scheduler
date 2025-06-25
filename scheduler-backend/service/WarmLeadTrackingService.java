package com.ubank.corp.dcr.api.service;

import com.ubank.corp.dcr.api.entity.WarmLeadTracking;
import com.ubank.corp.dcr.api.model.WarmLeadTrackingDTO;
import com.ubank.corp.dcr.api.repository.WarmLeadTrackingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class WarmLeadTrackingService {

    private final WarmLeadTrackingRepository warmLeadRepository;

    public WarmLeadTrackingService(WarmLeadTrackingRepository warmLeadRepository) {
        this.warmLeadRepository = warmLeadRepository;
        log.info("WarmLeadTrackingService initialized");
    }

    /**
     * Tracks a warm lead visit - creates new or updates existing
     */
    @Transactional
    public WarmLeadTrackingDTO trackWarmLeadVisit(WarmLeadTrackingDTO trackingDTO) {
        log.info("Tracking warm lead visit for user: {}, campaign: {}, product: {}", 
                trackingDTO.getUserIdentifier(), trackingDTO.getCampaignId(), trackingDTO.getInsightSubType());

        // Check if warm lead already exists for this user/campaign/product combination
        Optional<WarmLeadTracking> existingWarmLead = warmLeadRepository
            .findActiveWarmLead(trackingDTO.getUserIdentifier(), 
                               trackingDTO.getCampaignId(), 
                               trackingDTO.getInsightSubType());

        WarmLeadTracking warmLead;
        
        if (existingWarmLead.isPresent()) {
            // Update existing warm lead
            warmLead = existingWarmLead.get();
            warmLead.incrementVisitCount();
            warmLead.setUserAgent(trackingDTO.getUserAgent());
            warmLead.setReferrerUrl(trackingDTO.getReferrerUrl());
            
            log.info("Updated existing warm lead. New visit count: {}", warmLead.getVisitCount());
        } else {
            // Create new warm lead
            warmLead = new WarmLeadTracking();
            warmLead.setUserIdentifier(trackingDTO.getUserIdentifier());
            warmLead.setCampaignId(trackingDTO.getCampaignId());
            warmLead.setInsightSubType(trackingDTO.getInsightSubType());
            warmLead.setVisitCount(1);
            warmLead.setFirstVisitDate(LocalDateTime.now());
            warmLead.setLastVisitDate(LocalDateTime.now());
            warmLead.setUserAgent(trackingDTO.getUserAgent());
            warmLead.setReferrerUrl(trackingDTO.getReferrerUrl());
            warmLead.setIsConvertedToHot(false);
            
            log.info("Created new warm lead");
        }

        WarmLeadTracking saved = warmLeadRepository.save(warmLead);
        
        return convertToDTO(saved, "Warm lead tracked successfully");
    }

    /**
     * Converts warm lead to hot lead (marks as converted)
     */
    @Transactional
    public WarmLeadTrackingDTO convertWarmLeadToHot(String userIdentifier, 
                                                    String campaignId, 
                                                    String insightSubType) {
        log.info("Converting warm lead to hot for user: {}, campaign: {}, product: {}", 
                userIdentifier, campaignId, insightSubType);

        Optional<WarmLeadTracking> warmLeadOpt = warmLeadRepository
            .findActiveWarmLead(userIdentifier, campaignId, insightSubType);

        if (warmLeadOpt.isEmpty()) {
            log.warn("No active warm lead found for conversion");
            throw new IllegalArgumentException("No active warm lead found for this user/campaign/product combination");
        }

        WarmLeadTracking warmLead = warmLeadOpt.get();
        warmLead.markAsConvertedToHot();
        
        WarmLeadTracking saved = warmLeadRepository.save(warmLead);
        
        log.info("Successfully converted warm lead to hot. Total visits before conversion: {}", 
                saved.getVisitCount());
        
        return convertToDTO(saved, "Warm lead converted to hot lead successfully");
    }

    /**
     * Gets current warm lead statistics
     */
    public Optional<WarmLeadTrackingDTO> getWarmLeadStats(String userIdentifier, 
                                                         String campaignId, 
                                                         String insightSubType) {
        Optional<WarmLeadTracking> warmLead = warmLeadRepository
            .findActiveWarmLead(userIdentifier, campaignId, insightSubType);
        
        return warmLead.map(lead -> convertToDTO(lead, "Warm lead statistics retrieved"));
    }

    /**
     * Helper method to convert entity to DTO
     */
    private WarmLeadTrackingDTO convertToDTO(WarmLeadTracking entity, String message) {
        return WarmLeadTrackingDTO.builder()
            .id(entity.getId())
            .userIdentifier(entity.getUserIdentifier())
            .campaignId(entity.getCampaignId())
            .insightSubType(entity.getInsightSubType())
            .visitCount(entity.getVisitCount())
            .firstVisitDate(entity.getFirstVisitDate())
            .lastVisitDate(entity.getLastVisitDate())
            .userAgent(entity.getUserAgent())
            .referrerUrl(entity.getReferrerUrl())
            .isConvertedToHot(entity.getIsConvertedToHot())
            .convertedToHotDate(entity.getConvertedToHotDate())
            .message(message)
            .build();
    }
}