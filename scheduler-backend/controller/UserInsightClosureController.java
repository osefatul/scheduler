package com.usbank.corp.dcr.api.controller;

import com.usbank.corp.dcr.api.model.*;
import com.usbank.corp.dcr.api.service.UserInsightClosureService;

import entity.UserInsightClosure;

import com.usbank.corp.dcr.api.exception.DataHandlingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import model.ClosurePreferenceRequestDTO;
import model.ClosureStatisticsDTO;
import model.GlobalOptOutRequestDTO;
import model.InsightClosureRequestDTO;
import model.InsightClosureResponseDTO;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
  public ResponseEntity<InsightClosureResponseDTO> closeInsight(
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
   * Handle user's preference response
   */
  @PostMapping("/preference")
  @Operation(summary = "Set closure preference", 
             description = "Handle user's response to preference prompts (campaign-specific or global)")
  public ResponseEntity<String> setClosurePreference(
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
             description = "Handle user's request to opt out of all insights. Optional date parameter for testing.")
  public ResponseEntity<String> handleGlobalOptOut(
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
              
              return ResponseEntity.ok("User has been opted out of all insights");
          } else {
              // Handle opt-in if needed - this would need enableInsights method
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
   * Check if user is in wait period
   */
  @GetMapping("/check-wait-period/{userId}/{companyId}")
  @Operation(summary = "Check wait period status", 
             description = "Check if a user is in 1-month wait period")
  public ResponseEntity<Map<String, Object>> checkWaitPeriodStatus(
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
          
          return ResponseEntity.ok(response);
          
      } catch (Exception e) {
          log.error("Error checking wait period: {}", e.getMessage(), e);
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
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
  
  /**
   * Debug endpoint - Get detailed closure status
   */
  @GetMapping("/debug/closure-status/{userId}/{companyId}/{campaignId}")
  @Operation(summary = "Debug closure status", description = "Get detailed closure status for debugging")
  public ResponseEntity<Map<String, Object>> debugClosureStatus(
          @PathVariable String userId,
          @PathVariable String companyId,
          @PathVariable String campaignId,
          @RequestParam(value = "date", required = false) String dateStr) {
      
      try {
          Date checkDate = parseEffectiveDate(dateStr);
          Map<String, Object> debug = new HashMap<>();
          
          // Basic info
          debug.put("userId", userId);
          debug.put("companyId", companyId);
          debug.put("campaignId", campaignId);
          debug.put("checkDate", checkDate);
          
          // Check closure record
          Optional<UserInsightClosure> closureOpt = closureService.getUserClosureHistory(userId, companyId, campaignId);
          if (closureOpt.isPresent()) {
              UserInsightClosure closure = closureOpt.get();
              debug.put("closureExists", true);
              debug.put("closureCount", closure.getClosureCount());
              debug.put("permanentlyClosed", closure.getPermanentlyClosed());
              debug.put("nextEligibleDate", closure.getNextEligibleDate());
              debug.put("lastClosureDate", closure.getLastClosureDate());
              debug.put("closureReason", closure.getClosureReason());
              
              // Check if next eligible date is after check date
              if (closure.getNextEligibleDate() != null) {
                  debug.put("nextEligibleAfterCheckDate", closure.getNextEligibleDate().after(checkDate));
              }
          } else {
              debug.put("closureExists", false);
          }
          
          // Check methods
          debug.put("isCampaignClosed", closureService.isCampaignClosedForUser(userId, companyId, campaignId, checkDate));
          debug.put("isGloballyOptedOut", closureService.isUserGloballyOptedOut(userId, checkDate));
          debug.put("isInWaitPeriod", closureService.isUserInWaitPeriod(userId, companyId, checkDate));
          
          List<String> temporarilyClosedCampaigns = closureService.getClosedCampaignIds(userId, companyId, checkDate);
          debug.put("temporarilyClosedCampaigns", temporarilyClosedCampaigns);
          debug.put("isCampaignTemporarilyClosed", temporarilyClosedCampaigns.contains(campaignId));
          
          List<String> permanentlyBlockedCampaigns = closureService.getPermanentlyBlockedCampaignIds(userId, companyId);
          debug.put("permanentlyBlockedCampaigns", permanentlyBlockedCampaigns);
          debug.put("isCampaignPermanentlyBlocked", permanentlyBlockedCampaigns.contains(campaignId));
          
          return ResponseEntity.ok(debug);
          
      } catch (Exception e) {
          Map<String, Object> error = new HashMap<>();
          error.put("error", e.getMessage());
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
      }
  }
  
  /**
   * Debug endpoint - Get all closures for a user
   */
  @GetMapping("/debug/all-closures/{userId}/{companyId}")
  @Operation(summary = "Debug all closures", description = "Get all closure records for a user")
  public ResponseEntity<List<Map<String, Object>>> debugAllClosures(
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
          
          return ResponseEntity.ok(result);
          
      } catch (Exception e) {
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
      }
  }
  
  /**
   * Reset closure for testing purposes
   */
  @PostMapping("/debug/reset-closure")
  @Operation(summary = "Reset closure (DEBUG)", description = "Reset closure count for testing")
  public ResponseEntity<String> resetClosure(@RequestBody Map<String, String> request) {
      try {
          String userId = request.get("userId");
          String companyId = request.get("companyId");
          String campaignId = request.get("campaignId");
          
          closureService.resetCampaignClosure(userId, companyId, campaignId);
          return ResponseEntity.ok("Closure reset successfully");
          
      } catch (Exception e) {
          log.error("Error resetting closure: {}", e.getMessage(), e);
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                  .body("Error resetting closure");
      }
  }
  
  // Helper method to parse date parameter
  private Date parseEffectiveDate(String dateStr) {
      if (dateStr == null || dateStr.isEmpty()) {
          return new Date(); // Default to current date
      }
      
      try {
          // Support multiple date formats
          SimpleDateFormat[] formats = {
              new SimpleDateFormat("yyyy-MM-dd"),
              new SimpleDateFormat("yyyyMMdd"),
              new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
              new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
          };
          
          for (SimpleDateFormat format : formats) {
              try {
                  return format.parse(dateStr);
              } catch (ParseException e) {
                  // Try next format
              }
          }
          
          // If no format works, try to parse as timestamp
          long timestamp = Long.parseLong(dateStr);
          return new Date(timestamp);
          
      } catch (Exception e) {
          log.warn("Could not parse date '{}', using current date", dateStr);
          return new Date();
      }
  }
  
  private InsightClosureResponseDTO createErrorResponse(String message) {
      InsightClosureResponseDTO response = new InsightClosureResponseDTO();
      response.setMessage(message);
      response.setAction("ERROR");
      return response;
  }
}
