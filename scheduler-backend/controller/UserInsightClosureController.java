package com.usbank.corp.dcr.api.controller;

import com.usbank.corp.dcr.api.model.*;
import com.usbank.corp.dcr.api.service.UserInsightClosureService;
import com.usbank.corp.dcr.api.entity.UserInsightClosure;
import com.usbank.corp.dcr.api.exception.DataHandlingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
               description = "Records when a user closes an insight/campaign. Optional date parameter for testing.")
    public ResponseEntity<ApiResponse<InsightClosureResponseDTO>> closeInsight(
            @Valid @RequestBody InsightClosureRequestDTO request) {
        try {
            Date effectiveDate = parseEffectiveDate(request.getClosureDate());
            log.info("Closing insight for user: {}, campaign: {} at date: {}", 
                    request.getUserId(), request.getCampaignId(), effectiveDate);
            InsightClosureResponseDTO response = closureService.recordInsightClosure(
                    request.getUserId(), 
                    request.getCompanyId(), 
                    request.getCampaignId(),
                    effectiveDate);
            return ResponseEntity.ok(ApiResponse.success(response,"Insight Closed SuccessFully"));
            
        } catch (DataHandlingException e) {
            log.error("Error closing insight: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            		.body(ApiResponse.failure("An unexpected error occurred"));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            		.body(ApiResponse.failure("An unexpected error occurred"));
        }
    }
    
    /**
     * Handle user's preference response
     */
    @PostMapping("/preference")
    @Operation(summary = "Set closure preference", 
               description = "Handle user's response to preference prompts (campaign-specific or global)")
    public ResponseEntity<ApiResponse<String>> setClosurePreference(
            @Valid @RequestBody PreferenceRequestDTO request) {
        try {
            Date effectiveDate = parseEffectiveDate(request.getPreferenceDate());
            
            log.info("Setting preference for user: {}, campaign: {}, wantsToSee: {}, isGlobal: {} at date: {}", 
                    request.getUserId(), request.getCampaignId(), request.getWantsToSee(), 
                    request.getIsGlobalResponse(), effectiveDate);
            
            closureService.handlePreferenceResponse(
                    request.getUserId(),
                    request.getCompanyId(),
                    request.getCampaignId(),
                    request.getWantsToSee(),
                    request.getReason(),
                    request.getIsGlobalResponse(),
                    effectiveDate);
            
            return ResponseEntity.ok(ApiResponse.success("User preference updated successfully","Preference Updated Successfully"));
            
        } catch (DataHandlingException e) {
            log.error("Error setting preference: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure("Error setting preference: "));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            		.body(ApiResponse.failure("An unexpected error occurred"));
        }
    }
    
    /**
     * Handle global opt-out request 
     */
    @PostMapping("/global-optout")
    @Operation(summary = "Global opt-out", 
               description = "Handle user's request to opt out of all insights. Optional date parameter for testing.")
    public ResponseEntity<ApiResponse<String>> handleGlobalOptOut(
            @Valid @RequestBody GlobalOptOutRequestDTO request) {
        try {
            Date effectiveDate = parseEffectiveDate(request.getOptOutDate());
            
            log.info("Processing global opt-out for user: {}, optOut: {} at date: {}", 
                    request.getUserId(), request.getOptOut(), effectiveDate);
            
            if (request.getOptOut()) {
                closureService.handleGlobalOptOut(
                        request.getUserId(),
                        request.getReason(),
                        effectiveDate);
                
                return ResponseEntity.ok(ApiResponse.success("User has been opted out of all insights","Global opt-out request Completed"));
            } else {
                // Handle opt-in if needed - this would need enableInsights method
                return ResponseEntity.ok(ApiResponse.success("User preference updated","Global opt-out request Completed"));
            }
            
        } catch (Exception e) {
            log.error("Error processing global opt-out: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            		.body(ApiResponse.failure("An unexpected error occurred"));
        }
    }
    
    /**
     * Check if a user is opted out
     */
    @GetMapping("/check-optout/{userId}")
    @Operation(summary = "Check opt-out status", 
               description = "Check if a user has opted out of insights")
    public ResponseEntity<ApiResponse<Boolean>> checkOptOutStatus(@PathVariable String userId) {
        try {
            boolean isOptedOut = closureService.isUserGloballyOptedOut(userId);
            return ResponseEntity.ok().body(ApiResponse.success(isOptedOut,"Checkout Status Completed"));
            
        } catch (Exception e) {
            log.error("Error checking opt-out status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure(String.valueOf(false)));
        }
    }
    
    /**
     * Check if user is in wait period
     */
    @GetMapping("/check-wait-period/{userId}/{companyId}")
    @Operation(summary = "Check wait period status", 
               description = "Check if a user is in 1-month wait period")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkWaitPeriodStatus(
            @PathVariable String userId,
            @PathVariable String companyId,
            @RequestParam(value = "date", required = false) String dateStr) {
        try {
            Date checkDate = parseEffectiveDate(dateStr);
            boolean inWaitPeriod = closureService.isUserInWaitPeriod(userId, companyId, checkDate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("companyId", companyId);
            response.put("checkDate", checkDate);
            response.put("inWaitPeriod", inWaitPeriod);
            
            if (inWaitPeriod) {
                List<CampaignWaitStatusDTO> waitStatus = closureService.getCampaignsInWaitPeriod(userId, companyId);
                response.put("waitDetails", waitStatus);
            }
            
            return ResponseEntity.ok().body(ApiResponse.success(response,"Wait period status Completed"));
            
        } catch (Exception e) {
            log.error("Error checking wait period: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure("Wait Period Status Failed"));
        }
    }
    
    /**
     * Get closure statistics for a campaign
     */
    @GetMapping("/statistics/{campaignId}")
    @Operation(summary = "Get closure statistics", 
               description = "Get closure statistics for a specific campaign")
    public ResponseEntity<ApiResponse<ClosureStatisticsDTO>> getClosureStatistics(
            @PathVariable String campaignId) {
        try {
            ClosureStatisticsDTO stats = closureService.getClosureStatistics(campaignId);
            return ResponseEntity.ok().body(ApiResponse.success(stats,"Closure Statistics Completed"));
             
        } catch (Exception e) {
            log.error("Error getting statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure("Error getting closure statistics"));
        }
    }

    @GetMapping("/debug/closure-status/{userId}/{companyId}/{campaignId}")
    @Operation(summary = "Debug closure status", description = "Get detailed closure status for debugging")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugClosureStatus(
            @PathVariable String userId,
            @PathVariable String companyId,
            @PathVariable String campaignId,
            @RequestParam(value = "date", required = false) String dateStr) {
        
        try {
            Date checkDate = parseEffectiveDate(dateStr);
            Map<String, Object> debug = new HashMap<>();
            
            debug.put("userId", userId);
            debug.put("companyId", companyId);
            debug.put("campaignId", campaignId);
            debug.put("checkDate", checkDate);
            
            Optional<UserInsightClosure> closureOpt = closureService.getUserClosureHistory(userId, companyId, campaignId);
            if (closureOpt.isPresent()) {
                UserInsightClosure closure = closureOpt.get();
                debug.put("closureExists", true);
                debug.put("closureCount", closure.getClosureCount());
                debug.put("permanentlyClosed", closure.getPermanentlyClosed());
                debug.put("nextEligibleDate", closure.getNextEligibleDate());
                debug.put("lastClosureDate", closure.getLastClosureDate());
                debug.put("closureReason", closure.getClosureReason());
                
                if (closure.getNextEligibleDate() != null) {
                    debug.put("nextEligibleAfterCheckDate", closure.getNextEligibleDate().after(checkDate));
                }
            } else {
                debug.put("closureExists", false);
            }
            
            debug.put("isCampaignClosed", closureService.isCampaignClosedForUser(userId, companyId, campaignId, checkDate));
            debug.put("isGloballyOptedOut", closureService.isUserGloballyOptedOut(userId, checkDate));
            debug.put("isInWaitPeriod", closureService.isUserInWaitPeriod(userId, companyId, checkDate));
            
            List<String> temporarilyClosedCampaigns = closureService.getClosedCampaignIds(userId, companyId, checkDate);
            debug.put("temporarilyClosedCampaigns", temporarilyClosedCampaigns);
            debug.put("isCampaignTemporarilyClosed", temporarilyClosedCampaigns.contains(campaignId));
            
            List<String> permanentlyBlockedCampaigns = closureService.getPermanentlyBlockedCampaignIds(userId, companyId);
            debug.put("permanentlyBlockedCampaigns", permanentlyBlockedCampaigns);
            debug.put("isCampaignPermanentlyBlocked", permanentlyBlockedCampaigns.contains(campaignId));
            
            return ResponseEntity.ok().body(ApiResponse.success(debug,"Debug Closure Status Completed"));
            
        } catch (Exception e) {
            //Map<String, Object> error = new HashMap<>();
            //error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure("Error debugging closure status"));
        }
    }
    
    @GetMapping("/debug/all-closures/{userId}/{companyId}")
    @Operation(summary = "Debug all closures", description = "Get all closure records for a user")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> debugAllClosures(
            @PathVariable String userId,
            @PathVariable String companyId) {
        
        try {
            List<UserInsightClosure> closures = closureService.getUserClosures(userId, companyId);
            List<Map<String, Object>> result = new ArrayList<>();
            
            for (UserInsightClosure closure : closures) {
                Map<String, Object> closureInfo = new HashMap<>();
                closureInfo.put("campaignId", closure.getCampaignId());
                closureInfo.put("closureCount", closure.getClosureCount());
                closureInfo.put("permanentlyClosed", closure.getPermanentlyClosed());
                closureInfo.put("nextEligibleDate", closure.getNextEligibleDate());
                closureInfo.put("lastClosureDate", closure.getLastClosureDate());
                closureInfo.put("closureReason", closure.getClosureReason());
                closureInfo.put("optOutAllInsights", closure.getOptOutAllInsights());
                result.add(closureInfo);
            }
            
            return ResponseEntity.ok().body(ApiResponse.success(result,"Debug All Closures Completed"));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure("Error retrieving all closures"));
        }
    }
    
    @PostMapping("/debug/reset-closure")
    @Operation(summary = "Reset closure (DEBUG)", description = "Reset closure count for testing")
    public ResponseEntity<ApiResponse<String>> resetClosure(@RequestBody Map<String, String> request) {
        try {
            String userId = request.get("userId");
            String companyId = request.get("companyId");
            String campaignId = request.get("campaignId");
            
            closureService.resetCampaignClosure(userId, companyId, campaignId);
            return ResponseEntity.ok().body(ApiResponse.success("Closure reset successfully", "Reset Closure Completed"));
             
        } catch (Exception e) {
            log.error("Error resetting closure: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure("Error resetting closure"));
        }
    }
    

    private Date parseEffectiveDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return new Date(); 
        }
        
        try {
            SimpleDateFormat[] formats = {
                new SimpleDateFormat("yyyy-MM-dd"),
                new SimpleDateFormat("yyyyMMdd"),
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            };
            
            for (SimpleDateFormat format : formats) {
                try {
                    return format.parse(dateStr);
                } catch (Exception e) {
                    log.warn("Could not parse date '{}', using current date", dateStr);
                    return new Date();
                }
            }
            
            long timestamp = Long.parseLong(dateStr);
            return new Date(timestamp);
            
        } catch (Exception e) {
            log.warn("Could not parse date '{}', using current date", dateStr);
            return new Date();
        }
    }
    
  }