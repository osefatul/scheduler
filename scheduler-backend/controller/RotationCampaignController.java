//new version for user layer rotation:
package com.usbank.corp.dcr.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.model.CampaignResponseDTO;
import com.usbank.corp.dcr.api.service.RotationCampaignService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import model.InsightClosureRequest;
import model.InsightClosureResponse;
import service.UserCampaignRotationService;
import service.UserInsightPreferenceService;
import service.UserSessionService;

@ /**
* Get the next eligible campaign
* 
* IMPORTANT: This already filters out campaigns user shouldn't see based on closure history
*/
@RequestMapping(method = RequestMethod.GET, value = "/next")
    public ResponseEntity<CampaignResponseDTO> getNextEligibleCampaign(
            @RequestParam("date") String date,
            @RequestParam("company") String company,
            @RequestParam("userId") String userId,
            HttpSession session) throws DataHandlingException {
        
        log.info("=== GET /next called ===");
        log.info("User: {}, Company: {}, Date: {}, Session: {}", userId, company, date, session.getId());
        
        try {
            // 1. Check user preferences
            if (!preferenceService.isUserEligibleForCampaigns(userId, company)) {
                String reason = preferenceService.getUserPreferenceSummary(userId, company);
                log.info("User {} not eligible: {}", userId, reason);
                throw new DataHandlingException(HttpStatus.OK.toString(), reason);
            }
            
            // 2. Get campaign (this will apply DB changes immediately on first view)
            CampaignResponseDTO campaign = rotationService.getNextEligibleCampaignWithSession(
                    date, company, userId, session);
            
            log.info("=== RESPONSE ===");
            log.info("Campaign: {}", campaign.getId());
            log.info("DisplayCapping: {}", campaign.getDisplayCapping());
            log.info("FrequencyPerWeek: {}", campaign.getFrequencyPerWeek());
            log.info("AlreadyViewedInSession: {}", campaign.getAlreadyViewedInSession());
            log.info("SessionId: {}", campaign.getSessionId());
            
            return ResponseEntity.ok(campaign);
            
        } catch (DataHandlingException e) {
            log.error("DataHandlingException: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                    "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * CORRECTED: End session - only cleanup session trackers
     */
    @RequestMapping(method = RequestMethod.POST, value = "/end-session")
    public ResponseEntity<String> endSession(HttpSession session) {
        log.info("=== POST /end-session called ===");
        log.info("Session: {}", session.getId());
        
        try {
            sessionService.endSession(session);
            
            // Also invalidate the HTTP session
            session.invalidate();
            
            log.info("Session ended and invalidated successfully");
            return ResponseEntity.ok("Session ended successfully - session trackers cleaned up");
            
        } catch (Exception e) {
            log.error("Error ending session: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error ending session: " + e.getMessage());
        }
    }
    
    /**
     * Debug endpoint to check database state
     */
    @RequestMapping(method = RequestMethod.GET, value = "/debug-tracker")
    public ResponseEntity<String> debugTracker(
            @RequestParam("userId") String userId,
            @RequestParam("company") String companyId,
            @RequestParam("campaignId") String campaignId) {
        
        try {
            // This would require injecting UserCampaignTrackerRepository
            // For now, just return session info
            return ResponseEntity.ok("Check logs for tracker information");
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

/**
* Handle user closing an insight - THIS IS WHERE THE MAGIC HAPPENS
* 
* The backend automatically detects:
* - How many times user has closed this campaign before
* - What modal/action to show based on closure count
* - Whether to show dialog or just hide
*/
@RequestMapping(method = RequestMethod.POST, value = "/close-insight")
public ResponseEntity<InsightClosureResponse> closeInsight(
       @RequestBody InsightClosureRequest request,
       HttpSession session) {
   
   log.info("Handling insight closure for user {} campaign {}", 
           request.getUserId(), request.getCampaignId());
   
   try {
       request.setSessionId(session.getId());
       
       // THIS METHOD AUTOMATICALLY DETECTS CLOSURE COUNT AND DETERMINES ACTION
       InsightClosureResponse response = preferenceService.handleInsightClosure(request);
       
       log.info("Processed insight closure: action = {}, showDialog = {}, closureCount = {}", 
               response.getAction(), response.isShowDialog(), response.getClosureCount());
       
       return ResponseEntity.ok(response);
       
   } catch (Exception e) {
       log.error("Error handling insight closure: {}", e.getMessage(), e);
       return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
               .body(createErrorResponse("Error processing insight closure"));
   }
}

/**
* Get campaign interaction information - USEFUL FOR DEBUGGING
* 
* This tells you exactly what state the user is in with a specific campaign
*/
@RequestMapping(method = RequestMethod.GET, value = "/campaign-interaction-info")
public ResponseEntity<UserInsightPreferenceService.CampaignInteractionInfo> getCampaignInteractionInfo(
       @RequestParam("userId") String userId,
       @RequestParam("company") String company,
       @RequestParam("campaignId") String campaignId) {
   
   try {
       UserInsightPreferenceService.CampaignInteractionInfo info = 
               preferenceService.getCampaignInteractionInfo(userId, company, campaignId);
       
       return ResponseEntity.ok(info);
       
   } catch (Exception e) {
       log.error("Error getting campaign interaction info: {}", e.getMessage(), e);
       return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
   }
}

  /**
     * Handle user response to "see again" question
     */
    @RequestMapping(method = RequestMethod.POST, value = "/see-again-response")
    public ResponseEntity<InsightClosureResponse> handleSeeAgainResponse(
            @RequestBody SeeAgainRequest request) {
        
        log.info("Handling see again response for user {} campaign {}: {}", 
                request.getUserId(), request.getCampaignId(), request.isWantToSeeAgain());
        
        try {
            InsightClosureResponse response = preferenceService.handleSeeAgainResponse(
                    request.getUserId(), request.getCompanyId(), request.getCampaignId(), 
                    request.isWantToSeeAgain(), request.getReason());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error handling see again response: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error processing response"));
        }
    }
    
    /**
     * Handle user response to "stop all insights" question
     */
    @RequestMapping(method = RequestMethod.POST, value = "/stop-all-response")
    public ResponseEntity<InsightClosureResponse> handleStopAllResponse(
            @RequestBody StopAllRequest request) {
        
        log.info("Handling stop all response for user {} company {}: {}", 
                request.getUserId(), request.getCompanyId(), request.isStopAll());
        
        try {
            InsightClosureResponse response = preferenceService.handleStopAllResponse(
                    request.getUserId(), request.getCompanyId(), 
                    request.isStopAll(), request.getReason());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error handling stop all response: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error processing response"));
        }
    }
    
    private InsightClosureResponse createErrorResponse(String message) {
        InsightClosureResponse error = new InsightClosureResponse();
        error.setAction("ERROR");
        error.setMessage(message);
        return error;
    }

/**
 * Get session statistics
 * 
 * USAGE: Call this for debugging or monitoring session state
 * - Shows current session status and user eligibility
 */
@RequestMapping(method=RequestMethod.GET,value="/session-stats")public ResponseEntity<SessionStatsDTO>getSessionStats(@RequestParam("userId")String userId,@RequestParam("company")String company,HttpSession session){

try{SessionStatsDTO stats=new SessionStatsDTO();stats.setSessionId(session.getId());stats.setUserId(userId);stats.setCompanyId(company);stats.setUserEligible(preferenceService.isUserEligibleForCampaigns(userId,company));stats.setPreferenceSummary(preferenceService.getUserPreferenceSummary(userId,company));stats.setActiveCampaignsCount(sessionService.getActiveCampaignsCount(session,userId,company));

return ResponseEntity.ok(stats);

}catch(Exception e){log.error("Error getting session stats: {}",e.getMessage(),e);return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();}}

/**
 * Legacy endpoint for getting eligible campaigns for rotations
 * Maintained for backward compatibility
 */
@RequestMapping(method=RequestMethod.GET,value="/all")public ResponseEntity<CampaignResponseDTO>getEligibleCampaignsForRotations(@RequestParam("date")String date,@RequestParam("company")String company,@RequestParam(value="userId",required=false)String userId)throws DataHandlingException{

// If userId is provided, use the new user-level rotation
if(userId!=null&&!userId.isEmpty()){log.info("Legacy call with user: Getting eligible campaigns for user {} in company {} on date {}",userId,company,date);

CampaignResponseDTO campaign=rotationService.getNextEligibleCampaignForUser(date,company,userId);return ResponseEntity.ok(campaign);}else{
// For backward compatibility, throw an error or return a default response
log.warn("Legacy call without user ID is no longer supported");throw new DataHandlingException(HttpStatus.BAD_REQUEST.toString(),"User ID is required for campaign rotation");}}}

// package controller;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RequestMethod;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.bind.annotation.RestController;

// import com.usbank.corp.dcr.api.exception.DataHandlingException;
// import com.usbank.corp.dcr.api.model.CampaignResponseDTO;
// import com.usbank.corp.dcr.api.service.RotationCampaignService;

// import lombok.extern.slf4j.Slf4j;

// @RestController
// @RequestMapping(value = "api/v1/rotatecampaign/", produces =
// MediaType.APPLICATION_JSON_VALUE)
// @Slf4j
// public class RotationCampaignController {

// @Autowired
// RotationCampaignService campaignService;

// /**
// * Get the next eligible campaign for rotation based on company
// *
// * @param date Request date in format yyyyMMdd
// * @param company Company identifier
// * @return Next eligible campaign, or appropriate status if none available
// * @throws DataHandlingException if there's an issue with data handling
// */
// @RequestMapping(method = RequestMethod.GET, value = "/next")
// public ResponseEntity<CampaignResponseDTO> getNextEligibleCampaign(
// @RequestParam("date") String date,
// @RequestParam("company") String company) throws DataHandlingException {

// log.info("Getting next eligible campaign for company {} on date {}", company,
// date);

// CampaignResponseDTO campaign = campaignService.getNextEligibleCampaign(date,
// company);
// return ResponseEntity.ok(campaign);
// }

// /**
// * Legacy endpoint for getting eligible campaigns for rotations
// * Maintained for backward compatibility
// *
// * @param date Request date in format yyyyMMdd
// * @param company Company identifier
// * @return Next eligible campaign, or appropriate status if none available
// * @throws DataHandlingException if there's an issue with data handling
// */
// @RequestMapping(method = RequestMethod.GET, value = "/all")
// public ResponseEntity<CampaignResponseDTO> getEligibleCampaignsForRotations(
// @RequestParam("date") String date,
// @RequestParam("company") String company) throws DataHandlingException {

// log.info("Legacy call: Getting eligible campaigns for company {} on date {}",
// company, date);

// CampaignResponseDTO campaign =
// campaignService.updateEligibleCampaignsForRotations(date, company);
// return ResponseEntity.ok(campaign);
// }
// }