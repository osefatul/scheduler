package com.usbank.corp.dcr.api.controller;

import com.usbank.corp.dcr.api.model.*;
import com.usbank.corp.dcr.api.service.UserInsightClosureService;
import com.usbank.corp.dcr.api.exception.DataHandlingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/insights/closure")
@Tag(name = "Insight Closure", description = "APIs for managing user insight closures")
@Slf4j
@Validated
public class UserInsightClosureController {
    
    @Autowired
    private UserInsightClosureService closureService;
    
    /**
     * Record an insight closure
     */
    @PostMapping("/close")
    @Operation(summary = "Close an insight", 
               description = "Records when a user closes an insight/campaign")
    public ResponseEntity<InsightClosureResponseDTO> closeInsight(
            @Valid @RequestBody InsightClosureRequestDTO request) {
        try {
            log.info("Closing insight for user: {}, campaign: {}", 
                    request.getUserId(), request.getCampaignId());
            
            InsightClosureResponseDTO response = closureService.recordInsightClosure(
                    request.getUserId(), 
                    request.getCompanyId(), 
                    request.getCampaignId());
            
            return ResponseEntity.ok(response);
            
        } catch (DataHandlingException e) {
            log.error("Error closing insight: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("An unexpected error occurred"));
        }
    }
    
    /**
     * Handle user's preference after second closure
     */
    @PostMapping("/preference")
    @Operation(summary = "Set closure preference", 
               description = "Handle user's response to 'see this insight again?' prompt")
    public ResponseEntity<String> setClosurePreference(
            @Valid @RequestBody ClosurePreferenceRequestDTO request) {
        try {
            log.info("Setting closure preference for user: {}, campaign: {}, preference: {}", 
                    request.getUserId(), request.getCampaignId(), request.getWantsToSeeAgain());
            
            closureService.handleSecondClosureResponse(
                    request.getUserId(),
                    request.getCompanyId(),
                    request.getCampaignId(),
                    request.getWantsToSeeAgain(),
                    request.getReason());
            
            return ResponseEntity.ok("Preference recorded successfully");
            
        } catch (DataHandlingException e) {
            log.error("Error setting preference: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred");
        }
    }
    
    /**
     * Handle global opt-out request
     */
    @PostMapping("/global-optout")
    @Operation(summary = "Global opt-out", 
               description = "Handle user's request to opt out of all insights")
    public ResponseEntity<String> handleGlobalOptOut(
            @Valid @RequestBody GlobalOptOutRequestDTO request) {
        try {
            log.info("Processing global opt-out for user: {}, optOut: {}", 
                    request.getUserId(), request.getOptOut());
            
            if (request.getOptOut()) {
                closureService.handleGlobalOptOut(
                        request.getUserId(),
                        request.getReason());
                
                return ResponseEntity.ok("User has been opted out of all insights");
            } else {
                // Handle opt-in if needed
                return ResponseEntity.ok("User preference updated");
            }
            
        } catch (Exception e) {
            log.error("Error processing global opt-out: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred");
        }
    }
    
    /**
     * Check if a user is opted out
     */
    @GetMapping("/check-optout/{userId}")
    @Operation(summary = "Check opt-out status", 
               description = "Check if a user has opted out of insights")
    public ResponseEntity<Boolean> checkOptOutStatus(@PathVariable String userId) {
        try {
            boolean isOptedOut = closureService.isUserGloballyOptedOut(userId);
            return ResponseEntity.ok(isOptedOut);
            
        } catch (Exception e) {
            log.error("Error checking opt-out status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false);
        }
    }
    
    /**
     * Get closure statistics for a campaign
     */
    @GetMapping("/statistics/{campaignId}")
    @Operation(summary = "Get closure statistics", 
               description = "Get closure statistics for a specific campaign")
    public ResponseEntity<ClosureStatisticsDTO> getClosureStatistics(
            @PathVariable String campaignId) {
        try {
            ClosureStatisticsDTO stats = closureService.getClosureStatistics(campaignId);
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    private InsightClosureResponseDTO createErrorResponse(String message) {
        InsightClosureResponseDTO response = new InsightClosureResponseDTO();
        response.setMessage(message);
        response.setAction("ERROR");
        return response;
    }
}