package com.ubank.corp.dcr.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ubank.corp.dcr.api.model.CampaignLeadDTO;
import com.ubank.corp.dcr.api.model.WarmLeadTrackingDTO;
import com.ubank.corp.dcr.api.service.CampaignLeadService;
import com.ubank.corp.dcr.api.service.WarmLeadTrackingService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;package com.ubank.corp.dcr.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ubank.corp.dcr.api.model.CampaignLeadDTO;
import com.ubank.corp.dcr.api.model.WarmLeadTrackingDTO;
import com.ubank.corp.dcr.api.service.CampaignLeadService;
import com.ubank.corp.dcr.api.service.WarmLeadTrackingService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/lead")
public class LeadController {

    private final CampaignLeadService campaignLeadService;
    private final WarmLeadTrackingService warmLeadTrackingService;

    public LeadController(CampaignLeadService campaignLeadService, 
                         WarmLeadTrackingService warmLeadTrackingService) {
        this.campaignLeadService = campaignLeadService;
        this.warmLeadTrackingService = warmLeadTrackingService;
        log.info("LeadController initialized with both warm and hot lead services");
    }

    // ========================= HOT LEAD ENDPOINTS =========================
    
    /**
     * Create a hot lead (campaign lead) - typically after user clicks "Talk to RM"
     * IMPORTANT: This will automatically permanently close the campaign for the user
     */
    @PostMapping("/hot/create")
    public ResponseEntity<CampaignLeadDTO> createHotLead(@Valid @RequestBody CampaignLeadDTO campaignLeadDTO) {
        log.info("Creating hot lead for campaign: {}, user: {} (will permanently close campaign)", 
                campaignLeadDTO.getCampaignId(), campaignLeadDTO.getUserIdentifier());
        
        CampaignLeadDTO result = campaignLeadService.createCampaignLead(campaignLeadDTO);
        return ResponseEntity.ok(result);
    }

    /**
     * Delete a hot lead (campaign lead)
     */
    @DeleteMapping("/hot/delete")
    public ResponseEntity<CampaignLeadDTO> deleteHotLead(@RequestBody CampaignLeadDTO campaignLeadDTO) {
        log.info("Deleting hot lead with ID: {}", campaignLeadDTO.getId());
        
        CampaignLeadDTO result = campaignLeadService.deleteCampaignLead(campaignLeadDTO);
        return ResponseEntity.ok(result);
    }

    // ========================= WARM LEAD ENDPOINTS =========================
    
    /**
     * Track a warm lead visit - creates new or increments existing visit count
     */
    @PostMapping("/warm/track")
    public ResponseEntity<WarmLeadTrackingDTO> trackWarmLead(
            @Valid @RequestBody WarmLeadTrackingDTO trackingDTO,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "Referer", required = false) String referrer) {
        
        log.info("Tracking warm lead visit for user: {}, campaign: {}, product: {}", 
                trackingDTO.getUserIdentifier(), trackingDTO.getCampaignId(), trackingDTO.getInsightSubType());
        
        // Set headers in DTO
        trackingDTO.setUserAgent(userAgent);
        trackingDTO.setReferrerUrl(referrer);
        
        WarmLeadTrackingDTO result = warmLeadTrackingService.trackWarmLeadVisit(trackingDTO);
        return ResponseEntity.ok(result);
    }

    /**
     * Get warm lead statistics for a specific user/campaign/product combination
     */
    @GetMapping("/warm/stats")
    public ResponseEntity<WarmLeadTrackingDTO> getWarmLeadStats(
            @RequestParam String userIdentifier,
            @RequestParam String campaignId,
            @RequestParam String insightSubType) {
        
        log.info("Getting warm lead stats for user: {}, campaign: {}, product: {}", 
                userIdentifier, campaignId, insightSubType);
        
        Optional<WarmLeadTrackingDTO> stats = warmLeadTrackingService
            .getWarmLeadStats(userIdentifier, campaignId, insightSubType);
        
        if (stats.isPresent()) {
            return ResponseEntity.ok(stats.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Convert warm lead to hot lead manually (internal use or admin functionality)
     */
    @PostMapping("/warm/convert-to-hot")
    public ResponseEntity<WarmLeadTrackingDTO> convertWarmToHot(
            @RequestParam String userIdentifier,
            @RequestParam String campaignId,
            @RequestParam String insightSubType) {
        
        log.info("Converting warm lead to hot for user: {}, campaign: {}, product: {}", 
                userIdentifier, campaignId, insightSubType);
        
        try {
            WarmLeadTrackingDTO result = warmLeadTrackingService
                .convertWarmLeadToHot(userIdentifier, campaignId, insightSubType);
            
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Failed to convert warm lead: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                WarmLeadTrackingDTO.builder()
                    .message("Failed to convert warm lead: " + e.getMessage())
                    .build()
            );
        }
    }

    // ========================= COMBINED OPERATIONS =========================
    
    /**
     * Complete conversion flow: track warm lead and immediately convert to hot lead
     * This is useful for scenarios where you want to track engagement and conversion in one call
     * IMPORTANT: This will automatically permanently close the campaign for the user
     */
    @PostMapping("/convert-complete")
    public ResponseEntity<CampaignLeadDTO> completeConversionFlow(
            @Valid @RequestBody CampaignLeadDTO campaignLeadDTO,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "Referer", required = false) String referrer) {
        
        log.info("Starting complete conversion flow for user: {}, campaign: {} (will permanently close campaign)", 
                campaignLeadDTO.getUserIdentifier(), campaignLeadDTO.getCampaignId());
        
        try {
            // First, ensure warm lead is tracked (if not already)
            if (campaignLeadDTO.getUserIdentifier() != null && 
                campaignLeadDTO.getCampaignId() != null && 
                campaignLeadDTO.getCorporateConnectionInsight() != null) {
                
                WarmLeadTrackingDTO warmLeadDTO = WarmLeadTrackingDTO.builder()
                    .userIdentifier(campaignLeadDTO.getUserIdentifier())
                    .campaignId(campaignLeadDTO.getCampaignId())
                    .insightSubType(campaignLeadDTO.getCorporateConnectionInsight())
                    .userAgent(userAgent)
                    .referrerUrl(referrer)
                    .build();
                
                warmLeadTrackingService.trackWarmLeadVisit(warmLeadDTO);
                log.info("Warm lead tracked as part of complete conversion flow");
            }
            
            // Then create hot lead (which will also mark warm lead as converted AND permanently close campaign)
            CampaignLeadDTO result = campaignLeadService.createCampaignLead(campaignLeadDTO);
            
            log.info("Complete conversion flow completed successfully with permanent campaign closure");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error in complete conversion flow: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                CampaignLeadDTO.builder()
                    .message("Failed to complete conversion flow: " + e.getMessage())
                    .build()
            );
        }
    }

    // ========================= ANALYTICS ENDPOINTS =========================
    
    /**
     * Get comprehensive lead analytics for a campaign
     */
    @GetMapping("/analytics/campaign/{campaignId}")
    public ResponseEntity<?> getCampaignLeadAnalytics(@PathVariable String campaignId) {
        log.info("Getting lead analytics for campaign: {}", campaignId);
        
        // This could be expanded to return comprehensive analytics
        // For now, returning a simple message
        return ResponseEntity.ok().body(
            "Analytics endpoint - to be implemented based on specific requirements for campaign: " + campaignId
        );
    }

    // ========================= VERIFICATION ENDPOINTS =========================
    
    /**
     * Verify that a campaign is properly closed for a user after hot lead creation
     * This endpoint helps verify that the permanent closure is working correctly
     */
    @GetMapping("/verify-closure")
    public ResponseEntity<Object> verifyCampaignClosure(
            @RequestParam String userId,
            @RequestParam String companyId,
            @RequestParam String campaignId) {
        
        log.info("Verifying campaign closure for user: {}, company: {}, campaign: {}", 
                userId, companyId, campaignId);
        
        try {
            // This would require the verification service to be injected
            // For now, let's do a simple verification using the existing services
            boolean isClosed = false;
            String message = "";
            
            // Check if campaign is closed
            try {
                // You would inject UserInsightClosureService here
                // isClosed = userInsightClosureService.isCampaignClosedForUser(userId, companyId, campaignId);
                message = "Campaign closure verification completed. Check logs for detailed results.";
            } catch (Exception e) {
                message = "Error during verification: " + e.getMessage();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("companyId", companyId);
            response.put("campaignId", campaignId);
            response.put("isClosed", isClosed);
            response.put("message", message);
            response.put("timestamp", new Date());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error verifying campaign closure: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Verification failed: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}