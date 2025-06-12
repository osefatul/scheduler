package com.usbank.corp.dcr.api.controller;

import com.usbank.corp.dcr.api.service.UserCampaignRotationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.model.ApiResponse;
import com.usbank.corp.dcr.api.model.CampaignResponseDTO;
import com.usbank.corp.dcr.api.service.RotationCampaignService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(value = "api/v1/rotatecampaign/", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class RotationCampaignController {

	@Autowired
	RotationCampaignService campaignService;

	@Autowired
	private UserCampaignRotationService rotationService;

	/**
	 * Get the next eligible campaign for rotation based on company and required
	 * user
	 *
	 * @param date    Request date in format yyyyMMdd
	 * @param company Company identifier
	 * @param userId  User identifier for user-level rotation
	 * @return Next eligible campaign, or appropriate status if none available
	 * @throws DataHandlingException if there's an issue with data handling
	 */
    @RequestMapping(method = RequestMethod.GET, value = "/next")
    public ResponseEntity<ApiResponse<CampaignResponseDTO>> getNextEligibleCampaign(
            @RequestParam("date") String date,
            @RequestParam("company") String company,
            @RequestParam("userId") String userId) {
        try {
            log.info("Getting next eligible campaign for user {} in company {} on date {}",
                    userId, company, date);

            CampaignResponseDTO campaign = rotationService.getNextEligibleCampaignForUser(date, company, userId);
            return ResponseEntity.ok(ApiResponse.success(campaign, "Next eligible campaign found"));
        } catch (DataHandlingException e) {
            log.warn("Data handling exception: {}", e.getMessage());
            return ResponseEntity
                    .status(200)
                    .body(ApiResponse.failure(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(500)
                    .body(ApiResponse.failure("Internal server error: " + e.getMessage()));
        }
    }
	/**
	 * OLD endpoint for getting eligible campaigns for rotations
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/all")
	public ResponseEntity<CampaignResponseDTO> getEligibleCampaignsForRotations(
			@RequestParam("date") String date,
			@RequestParam("company") String company) throws DataHandlingException {

		log.info("Legacy call: Getting eligible campaigns for company {} on date {}", company, date);

		CampaignResponseDTO campaign = campaignService.updateEligibleCampaignsForRotations(date, company);
		return ResponseEntity.ok(campaign);
	}
}



















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
// @RequestMapping(value = "api/v1/rotatecampaign/", produces = MediaType.APPLICATION_JSON_VALUE)
// @Slf4j
// public class RotationCampaignController {

//     @Autowired
//     RotationCampaignService campaignService;
    
//     /**
//      * Get the next eligible campaign for rotation based on company
//      * 
//      * @param date Request date in format yyyyMMdd
//      * @param company Company identifier
//      * @return Next eligible campaign, or appropriate status if none available
//      * @throws DataHandlingException if there's an issue with data handling
//      */
//     @RequestMapping(method = RequestMethod.GET, value = "/next")
//     public ResponseEntity<CampaignResponseDTO> getNextEligibleCampaign(
//             @RequestParam("date") String date,
//             @RequestParam("company") String company) throws DataHandlingException {
        
//         log.info("Getting next eligible campaign for company {} on date {}", company, date);
        
//         CampaignResponseDTO campaign = campaignService.getNextEligibleCampaign(date, company);
//         return ResponseEntity.ok(campaign);
//     }
    
//     /**
//      * Legacy endpoint for getting eligible campaigns for rotations
//      * Maintained for backward compatibility
//      * 
//      * @param date Request date in format yyyyMMdd
//      * @param company Company identifier
//      * @return Next eligible campaign, or appropriate status if none available
//      * @throws DataHandlingException if there's an issue with data handling
//      */
//     @RequestMapping(method = RequestMethod.GET, value = "/all")
//     public ResponseEntity<CampaignResponseDTO> getEligibleCampaignsForRotations(
//             @RequestParam("date") String date,
//             @RequestParam("company") String company) throws DataHandlingException {
        
//         log.info("Legacy call: Getting eligible campaigns for company {} on date {}", company, date);
        
//         CampaignResponseDTO campaign = campaignService.updateEligibleCampaignsForRotations(date, company);
//         return ResponseEntity.ok(campaign);
//     }
// }