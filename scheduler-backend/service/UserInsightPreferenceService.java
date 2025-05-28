package com.usbank.corp.dcr.api.service;

import com.usbank.corp.dcr.api.entity.UserInsightPreference;
import com.usbank.corp.dcr.api.repository.UserInsightPreferenceRepository;
import com.usbank.corp.dcr.api.model.InsightClosureRequest;
import com.usbank.corp.dcr.api.model.InsightClosureResponse;
import lombok.extern.slf4j.Slf4j;
import model.CampaignInteractionInfo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class UserInsightPreferenceService {
    
    @Autowired
    private UserInsightPreferenceRepository preferenceRepository;


    /**
     * MAIN METHOD: Handle user closing an insight
     * This is where we detect what modal/action to show based on closure history
     */
    @Transactional
    public InsightClosureResponse handleInsightClosure(InsightClosureRequest request) {
        String userId = request.getUserId();
        String companyId = request.getCompanyId();
        String campaignId = request.getCampaignId();
        
        log.info("Handling insight closure for user {} campaign {}", userId, campaignId);
        
        // STEP 1: Get existing preference record for this specific campaign
        Optional<UserInsightPreference> existingPref = preferenceRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
        
        UserInsightPreference preference;
        int currentClosureCount;
        
        if (existingPref.isPresent()) {
            preference = existingPref.get();
            currentClosureCount = preference.getClosureCount();
            
            // Increment closure count
            preference.setClosureCount(currentClosureCount + 1);
            preference.setUpdatedDate(new Date());
            
            log.info("Found existing preference for user {} campaign {}: current closures = {}", 
                    userId, campaignId, currentClosureCount);
        } else {
            // First time closing this campaign
            preference = createNewPreference(userId, companyId, campaignId);
            preference.setClosureCount(1);
            currentClosureCount = 0; // Was 0, now will be 1
            
            log.info("Creating new preference for user {} campaign {}: first closure", userId, campaignId);
        }
        
        InsightClosureResponse response = new InsightClosureResponse();
        response.setUserId(userId);
        response.setCampaignId(campaignId);
        
        // STEP 2: Determine action based on NEW closure count
        int newClosureCount = preference.getClosureCount();
        
        if (newClosureCount == 1) {
            // FIRST CLOSURE
            preference.setPreferenceType(UserInsightPreference.PreferenceType.CLOSED_ONCE);
            
            response.setAction("HIDE_UNTIL_NEXT_ELIGIBLE");
            response.setMessage("This insight will be hidden until you're next eligible to see it.");
            response.setShowDialog(false);
            
            log.info("First closure - hiding until next eligible");
            
        } else if (newClosureCount == 2) {
            // SECOND CLOSURE - Show "See Again" Modal
            preference.setPreferenceType(UserInsightPreference.PreferenceType.CLOSED_TWICE);
            
            response.setAction("ASK_SEE_AGAIN");
            response.setMessage("Do you want to see this type of insight again?");
            response.setShowDialog(true);
            response.setDialogType("SEE_AGAIN");
            
            log.info("Second closure - showing 'see again' dialog");
            
        } else {
            // THIRD+ CLOSURE - Show "Stop All" Modal
            response.setAction("ASK_STOP_ALL");
            response.setMessage("Would you like to stop seeing all insights?");
            response.setShowDialog(true);
            response.setDialogType("STOP_ALL");
            
            log.info("Third+ closure (count: {}) - showing 'stop all' dialog", newClosureCount);
        }
        
        // STEP 3: Save the updated preference
        preferenceRepository.save(preference);
        
        log.info("Processed closure for user {} campaign {}: action = {}, newCount = {}", 
                userId, campaignId, response.getAction(), newClosureCount);
        
        return response;
    }
    




    
    /**
     * Check if user is eligible to see campaigns
     */
    public boolean isUserEligibleForCampaigns(String userId, String companyId) {
        // Check if user has opted out of all campaigns
        if (preferenceRepository.hasUserOptedOutOfAllCampaigns(userId, companyId)) {
            log.info("User {} has opted out of all campaigns for company {}", userId, companyId);
            return false;
        }
        
        // Check if user is in global cooling period
        if (preferenceRepository.isUserInGlobalCoolingPeriod(userId, companyId, new Date())) {
            log.info("User {} is in global cooling period for company {}", userId, companyId);
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if user should see a campaign based on their closure history
     * This is called BEFORE serving a campaign
     */
    public boolean isUserEligibleForCampaign(String userId, String companyId, String campaignId) {
      // First check general eligibility (opted out of all, global cooling, etc.)
      if (!isUserEligibleForCampaigns(userId, companyId)) {
          return false;
      }
      
      // Check campaign-specific preferences
      Optional<UserInsightPreference> campaignPref = preferenceRepository
              .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
      
      if (campaignPref.isPresent()) {
          UserInsightPreference pref = campaignPref.get();
          
          log.info("Found preference for user {} campaign {}: type = {}, closures = {}", 
                  userId, campaignId, pref.getPreferenceType(), pref.getClosureCount());
          
          // Check different preference states
          switch (pref.getPreferenceType()) {
              case CLOSED_ONCE:
                  // User closed once - can see again next time they're eligible
                  log.info("User {} closed campaign {} once - allowing to see again", userId, campaignId);
                  return true;
                  
              case CLOSED_TWICE:
                  // User closed twice but hasn't responded to "see again" question yet
                  // OR user responded "yes" to see again
                  log.info("User {} closed campaign {} twice - checking if they want to see again", userId, campaignId);
                  return true; // They can see it until they respond "no" to see again
                  
              case OPTED_OUT_CAMPAIGN:
                  // User specifically opted out of this campaign
                  log.info("User {} opted out of campaign {}", userId, campaignId);
                  return false;
                  
              case COOLING_PERIOD:
                  // User is in cooling period for this campaign
                  if (pref.isInCoolingPeriod()) {
                      log.info("User {} is in cooling period for campaign {} until {}", 
                              userId, campaignId, pref.getNextEligibleDate());
                      return false;
                  } else {
                      log.info("User {} cooling period for campaign {} has expired", userId, campaignId);
                      return true;
                  }
                  
              default:
                  return true;
          }
      }
      
      // No preference record means user hasn't interacted with this campaign
      log.info("No preference found for user {} campaign {} - eligible", userId, campaignId);
      return true;
  }



  /**
     * Get detailed information about user's interaction with a campaign
     * This can be used by frontend to understand the current state
     */
    public CampaignInteractionInfo getCampaignInteractionInfo(String userId, String companyId, String campaignId) {
        CampaignInteractionInfo info = new CampaignInteractionInfo();
        info.setUserId(userId);
        info.setCompanyId(companyId);
        info.setCampaignId(campaignId);
        
        Optional<UserInsightPreference> pref = preferenceRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
        
        if (pref.isPresent()) {
            UserInsightPreference preference = pref.get();
            info.setClosureCount(preference.getClosureCount());
            info.setPreferenceType(preference.getPreferenceType().toString());
            info.setInCoolingPeriod(preference.isInCoolingPeriod());
            info.setNextEligibleDate(preference.getNextEligibleDate());
            info.setOptOutReason(preference.getOptOutReason());
            
            // Determine what will happen on next closure
            int nextClosureCount = preference.getClosureCount() + 1;
            if (nextClosureCount == 1) {
                info.setNextClosureAction("HIDE_UNTIL_NEXT_ELIGIBLE");
            } else if (nextClosureCount == 2) {
                info.setNextClosureAction("ASK_SEE_AGAIN");
            } else {
                info.setNextClosureAction("ASK_STOP_ALL");
            }
        } else {
            // No interactions yet
            info.setClosureCount(0);
            info.setPreferenceType("NONE");
            info.setInCoolingPeriod(false);
            info.setNextClosureAction("HIDE_UNTIL_NEXT_ELIGIBLE");
        }
        
        info.setEligible(isUserEligibleForCampaign(userId, companyId, campaignId));
        
        return info;
    }
    
    
    /**
     * Handle user closing an insight
     */
    @Transactional
    public InsightClosureResponse handleInsightClosure(InsightClosureRequest request) {
        String userId = request.getUserId();
        String companyId = request.getCompanyId();
        String campaignId = request.getCampaignId();
        
        log.info("Handling insight closure for user {} campaign {}", userId, campaignId);
        
        // Get or create preference record
        Optional<UserInsightPreference> existingPref = preferenceRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
        
        UserInsightPreference preference;
        if (existingPref.isPresent()) {
            preference = existingPref.get();
            preference.setClosureCount(preference.getClosureCount() + 1);
            preference.setUpdatedDate(new Date());
        } else {
            preference = createNewPreference(userId, companyId, campaignId);
            preference.setClosureCount(1);
        }
        
        InsightClosureResponse response = new InsightClosureResponse();
        response.setUserId(userId);
        response.setCampaignId(campaignId);
        
        // Handle based on closure count
        if (preference.getClosureCount() == 1) {
            // First closure - just mark as closed once
            preference.setPreferenceType(UserInsightPreference.PreferenceType.CLOSED_ONCE);
            response.setAction("HIDE_UNTIL_NEXT_ELIGIBLE");
            response.setMessage("This insight will be hidden until you're next eligible to see it.");
            
        } else if (preference.getClosureCount() == 2) {
            // Second closure - ask if they want to see it again
            preference.setPreferenceType(UserInsightPreference.PreferenceType.CLOSED_TWICE);
            response.setAction("ASK_SEE_AGAIN");
            response.setMessage("Do you want to see this type of insight again?");
            response.setShowDialog(true);
            
        } else {
            // Subsequent closures - ask if they want to stop all insights
            response.setAction("ASK_STOP_ALL");
            response.setMessage("Would you like to stop seeing all insights?");
            response.setShowDialog(true);
        }
        
        preferenceRepository.save(preference);
        
        log.info("Processed closure for user {} campaign {}: action = {}", 
                userId, campaignId, response.getAction());
        
        return response;
    }
    
    /**
     * Handle user response to "see again" question
     */
    @Transactional
    public InsightClosureResponse handleSeeAgainResponse(
            String userId, String companyId, String campaignId, boolean wantToSeeAgain, String reason) {
        
        Optional<UserInsightPreference> prefOpt = preferenceRepository
                .findByUserIdAndCompanyIdAndCampaignId(userId, companyId, campaignId);
        
        if (!prefOpt.isPresent()) {
            throw new RuntimeException("Preference not found for user response");
        }
        
        UserInsightPreference preference = prefOpt.get();
        InsightClosureResponse response = new InsightClosureResponse();
        response.setUserId(userId);
        response.setCampaignId(campaignId);
        
        if (wantToSeeAgain) {
            // User wants to continue seeing this insight
            preference.setPreferenceType(UserInsightPreference.PreferenceType.CLOSED_TWICE);
            response.setAction("CONTINUE_NORMAL");
            response.setMessage("You'll continue to see this insight in normal rotation.");
            
        } else {
            // User doesn't want to see this insight again
            preference.setPreferenceType(UserInsightPreference.PreferenceType.COOLING_PERIOD);
            preference.setOptOutReason(reason);
            preference.setOptOutDate(new Date());
            
            // Set next eligible date to 1 month from now
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MONTH, 1);
            preference.setNextEligibleDate(calendar.getTime());
            
            response.setAction("ONE_MONTH_WAIT");
            response.setMessage("You won't see this insight for at least one month.");
        }
        
        preference.setUpdatedDate(new Date());
        preferenceRepository.save(preference);
        
        log.info("Processed see again response for user {} campaign {}: wantToSee = {}, action = {}", 
                userId, campaignId, wantToSeeAgain, response.getAction());
        
        return response;
    }
    
    /**
     * Handle user response to "stop all insights" question
     */
    @Transactional
    public InsightClosureResponse handleStopAllResponse(
            String userId, String companyId, boolean stopAll, String reason) {
        
        InsightClosureResponse response = new InsightClosureResponse();
        response.setUserId(userId);
        
        if (stopAll) {
            // User wants to stop all insights
            UserInsightPreference globalPref = createOrUpdateGlobalPreference(userId, companyId);
            globalPref.setPreferenceType(UserInsightPreference.PreferenceType.OPTED_OUT_ALL);
            globalPref.setOptOutReason(reason);
            globalPref.setOptOutDate(new Date());
            
            preferenceRepository.save(globalPref);
            
            response.setAction("OPTED_OUT_ALL");
            response.setMessage("You have been removed from all insight displays.");
            
            log.info("User {} opted out of all campaigns for company {}", userId, companyId);
            
        } else {
            // User wants to continue seeing insights but wait for this one
            UserInsightPreference globalPref = createOrUpdateGlobalPreference(userId, companyId);
            globalPref.setPreferenceType(UserInsightPreference.PreferenceType.COOLING_PERIOD);
            globalPref.setOptOutDate(new Date());
            
            // Set next eligible date to 1 month from now
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MONTH, 1);
            globalPref.setNextEligibleDate(calendar.getTime());
            
            preferenceRepository.save(globalPref);
            
            response.setAction("GLOBAL_ONE_MONTH_WAIT");
            response.setMessage("You won't see any insights for at least one month.");
            
            log.info("User {} entered global cooling period for company {}", userId, companyId);
        }
        
        return response;
    }
    
    /**
     * Create new preference record
     */
    private UserInsightPreference createNewPreference(String userId, String companyId, String campaignId) {
        UserInsightPreference preference = new UserInsightPreference();
        preference.setId(UUID.randomUUID().toString());
        preference.setUserId(userId);
        preference.setCompanyId(companyId);
        preference.setCampaignId(campaignId);
        preference.setCreatedDate(new Date());
        preference.setClosureCount(0);
        return preference;
    }
    
    /**
     * Create or update global preference (for all campaigns)
     */
    private UserInsightPreference createOrUpdateGlobalPreference(String userId, String companyId) {
        Optional<UserInsightPreference> existing = preferenceRepository
                .findByUserIdAndCompanyIdAndCampaignIdIsNull(userId, companyId);
        
        UserInsightPreference preference;
        if (existing.isPresent()) {
            preference = existing.get();
            preference.setUpdatedDate(new Date());
        } else {
            preference = createNewPreference(userId, companyId, null); // null campaignId = global
        }
        
        return preference;
    }
    
    /**
     * Get user's preference summary
     */
    public String getUserPreferenceSummary(String userId, String companyId) {
        if (!isUserEligibleForCampaigns(userId, companyId)) {
            if (preferenceRepository.hasUserOptedOutOfAllCampaigns(userId, companyId)) {
                return "User has opted out of all campaigns";
            } else {
                return "User is in global cooling period";
            }
        }
        return "User is eligible for campaigns";
    }
}