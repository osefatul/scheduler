package com.usbank.corp.dcr.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usbank.corp.dcr.api.exception.DataHandlingException;
import com.usbank.corp.dcr.api.model.CampaignResponseDTO;
import com.usbank.corp.dcr.api.service.RotationCampaignService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(value = "api/v1/rotatecampaign/", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class RotationCampaignController {

    @Autowired
    RotationCampaignService campaignService;
    
    /**
     * Get the next eligible campaign for a user
     * 
     * @param date Request date in format yyyyMMdd
     * @param company Company identifier
     * @param userId User identifier
     * @return Next eligible campaign, or appropriate status if none available
     * @throws DataHandlingException if there's an issue with data handling
     */
    @RequestMapping(method = RequestMethod.GET, value = "/next")
    public ResponseEntity<CampaignResponseDTO> getNextEligibleCampaign(
            @RequestParam("date") String date,
            @RequestParam("company") String company,
            @RequestParam("userId") String userId) throws DataHandlingException {
        
        log.info("Getting next eligible campaign for user {} from company {} on date {}", 
                userId, company, date);
        
        CampaignResponseDTO campaign = campaignService.getNextEligibleCampaign(date, company, userId);
        return ResponseEntity.ok(campaign);
    }
    
    /**
     * Record that a user has clicked "Learn More" on a campaign
     * 
     * @param campaignId Campaign identifier
     * @param userId User identifier
     * @return Success response
     */
    @PostMapping("/{campaignId}/learnmore")
    public ResponseEntity<String> recordLearnMoreClick(
            @PathVariable("campaignId") String campaignId,
            @RequestParam("userId") String userId) {
        
        log.info("Recording learn more click for campaign {} by user {}", campaignId, userId);
        
        campaignService.recordLearnMoreClick(campaignId, userId);
        return ResponseEntity.ok("Learn more click recorded");
    }
    
    /**
     * Record that a user has submitted a form for a campaign
     * 
     * @param campaignId Campaign identifier
     * @param userId User identifier
     * @return Success response
     */
    @PostMapping("/{campaignId}/formsubmit")
    public ResponseEntity<String> recordFormSubmission(
            @PathVariable("campaignId") String campaignId,
            @RequestParam("userId") String userId) {
        
        log.info("Recording form submission for campaign {} by user {}", campaignId, userId);
        
        campaignService.recordFormSubmission(campaignId, userId);
        return ResponseEntity.ok("Form submission recorded");
    }
}