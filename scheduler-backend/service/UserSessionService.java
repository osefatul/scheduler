// Service: UserSessionService.java
package com.usbank.corp.dcr.api.service;

import com.usbank.corp.dcr.api.entity.UserSessionTracker;
import com.usbank.corp.dcr.api.entity.UserCampaignTracker;
import com.usbank.corp.dcr.api.repository.UserSessionTrackerRepository;
import com.usbank.corp.dcr.api.repository.UserCampaignTrackerRepository;
import com.usbank.corp.dcr.api.util.RotationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpSession;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class UserSessionService {
    
    @Autowired
    private UserSessionTrackerRepository sessionTrackerRepository;
    
    @Autowired
    private UserCampaignTrackerRepository userCampaignTrackerRepository;
    
    @Autowired
    private RotationUtils rotationUtils;
    
    /**
     * Check if campaign has been viewed in current session
     */
    public boolean hasCampaignBeenViewedInSession(HttpSession session, String userId, String companyId, String campaignId) {
        String sessionId = session.getId();
        
        Optional<UserSessionTracker> tracker = sessionTrackerRepository
                .findBySessionIdAndUserIdAndCompanyIdAndCampaignIdAndSessionActiveTrue(
                        sessionId, userId, companyId, campaignId);
        
        return tracker.map(UserSessionTracker::getViewedInSession).orElse(false);
    }
    
    /**
     * Mark campaign as viewed in session (only first time in session counts)
     */
    @Transactional
    public boolean markCampaignViewedInSession(HttpSession session, String userId, String companyId, String campaignId) {
        String sessionId = session.getId();
        Date currentDate = new Date();
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        
        log.info("Attempting to mark campaign {} as viewed in session {} for user {}", campaignId, sessionId, userId);
        
        // Check if already viewed in this session
        Optional<UserSessionTracker> existingTracker = sessionTrackerRepository
                .findBySessionIdAndUserIdAndCompanyIdAndCampaignIdAndSessionActiveTrue(
                        sessionId, userId, companyId, campaignId);
        
        if (existingTracker.isPresent()) {
            UserSessionTracker tracker = existingTracker.get();
            if (tracker.getViewedInSession()) {
                // Already viewed in this session, just update activity
                tracker.updateActivity();
                sessionTrackerRepository.save(tracker);
                log.info("Campaign {} already viewed in session {} for user {} - no DB changes", campaignId, sessionId, userId);
                return false; // Not a new view
            } else {
                // Mark as viewed for first time in this session AND apply to DB
                tracker.markAsViewed();
                sessionTrackerRepository.save(tracker);
                
                // APPLY TO DATABASE IMMEDIATELY
                applyViewToDatabase(userId, companyId, campaignId, weekStartDate, currentDate);
                
                log.info("Marked campaign {} as viewed in session {} for user {} and applied to DB", campaignId, sessionId, userId);
                return true; // This is a new view
            }
        }
        
        // Create new session tracker, mark as viewed, and apply to DB
        UserSessionTracker newTracker = new UserSessionTracker();
        newTracker.setId(UUID.randomUUID().toString());
        newTracker.setSessionId(sessionId);
        newTracker.setUserId(userId);
        newTracker.setCompanyId(companyId);
        newTracker.setCampaignId(campaignId);
        newTracker.setViewedInSession(true);
        newTracker.setSessionStartTime(currentDate);
        newTracker.setLastActivityTime(currentDate);
        newTracker.setSessionActive(true);
        newTracker.setWeekStartDate(weekStartDate);
        
        sessionTrackerRepository.save(newTracker);
        
        // APPLY TO DATABASE IMMEDIATELY
        applyViewToDatabase(userId, companyId, campaignId, weekStartDate, currentDate);
        
        log.info("Created new session tracker and applied view to DB for user {} campaign {}", userId, campaignId);
        return true; // This is a new view
    }


    @Transactional
    private void applyViewToDatabase(String userId, String companyId, String campaignId, Date weekStartDate, Date currentDate) {
        try {
            log.info("Applying view to database immediately for user {} campaign {}", userId, campaignId);
            
            // Find the corresponding UserCampaignTracker
            Optional<UserCampaignTracker> persistentTrackerOpt = userCampaignTrackerRepository
                    .findByUserIdAndCompanyIdAndCampaignIdAndWeekStartDate(
                            userId, companyId, campaignId, weekStartDate);
            
            if (persistentTrackerOpt.isPresent()) {
                UserCampaignTracker persistentTracker = persistentTrackerOpt.get();
                
                // Log before changes
                log.info("Before applying view - weeklyFreq: {}, displayCap: {}", 
                        persistentTracker.getRemainingWeeklyFrequency(), 
                        persistentTracker.getRemainingDisplayCap());
                
                // Reduce frequency and display cap by 1 (immediate application)
                persistentTracker.setRemainingWeeklyFrequency(
                        Math.max(0, persistentTracker.getRemainingWeeklyFrequency() - 1));
                persistentTracker.setRemainingDisplayCap(
                        Math.max(0, persistentTracker.getRemainingDisplayCap() - 1));
                persistentTracker.setLastViewDate(currentDate);
                
                // Save the updated tracker
                userCampaignTrackerRepository.save(persistentTracker);
                
                log.info("Applied view to DB - NEW weeklyFreq: {}, NEW displayCap: {}", 
                        persistentTracker.getRemainingWeeklyFrequency(), 
                        persistentTracker.getRemainingDisplayCap());
                
            } else {
                log.error("UserCampaignTracker not found for user {} campaign {} week {}", 
                        userId, campaignId, weekStartDate);
                throw new RuntimeException("UserCampaignTracker not found - this should not happen");
            }
            
        } catch (Exception e) {
            log.error("Error applying view to database: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * End session and apply pending views to database
     */
    @Transactional
    public void endSession(HttpSession session) {
        String sessionId = session.getId();
        
        try {
            List<UserSessionTracker> activeTrackers = sessionTrackerRepository
                    .findBySessionIdAndSessionActiveTrue(sessionId);
            
            log.info("Ending session {} - found {} active trackers", sessionId, activeTrackers.size());
            
            // Just deactivate session trackers - DB changes already applied
            int deactivatedCount = sessionTrackerRepository.deactivateSession(sessionId);
            
            log.info("Ended session {} - deactivated {} session trackers (DB already updated)", 
                    sessionId, deactivatedCount);
            
        } catch (Exception e) {
            log.error("Error ending session {}: {}", sessionId, e.getMessage(), e);
        }
    }
    
    /**
     * Apply single session view to persistent database
     */
    @Transactional
    private void applySessionViewToDatabase(UserSessionTracker sessionTracker) {
        try {
            log.info("Applying session view to database for user {} campaign {}", 
                    sessionTracker.getUserId(), sessionTracker.getCampaignId());
            
            // Find the corresponding UserCampaignTracker
            Optional<UserCampaignTracker> persistentTrackerOpt = userCampaignTrackerRepository
                    .findByUserIdAndCompanyIdAndCampaignIdAndWeekStartDate(
                            sessionTracker.getUserId(),
                            sessionTracker.getCompanyId(),
                            sessionTracker.getCampaignId(),
                            sessionTracker.getWeekStartDate());
            
            UserCampaignTracker persistentTracker;
            if (persistentTrackerOpt.isPresent()) {
                persistentTracker = persistentTrackerOpt.get();
            } else {
                // Create new persistent tracker if not exists
                persistentTracker = createPersistentTrackerFromSession(sessionTracker);
            }
            
            // Reduce frequency and display cap by 1 (one session = one view)
            persistentTracker.setRemainingWeeklyFrequency(
                    Math.max(0, persistentTracker.getRemainingWeeklyFrequency() - 1));
            persistentTracker.setRemainingDisplayCap(
                    Math.max(0, persistentTracker.getRemainingDisplayCap() - 1));
            persistentTracker.setLastViewDate(new Date());
            
            // Save the updated tracker
            userCampaignTrackerRepository.save(persistentTracker);
            
            log.info("Applied session view: weeklyFreq={}, displayCap={}", 
                    persistentTracker.getRemainingWeeklyFrequency(), 
                    persistentTracker.getRemainingDisplayCap());
            
        } catch (Exception e) {
            log.error("Error applying session view to database: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Create persistent tracker from session data
     */
    private UserCampaignTracker createPersistentTrackerFromSession(UserSessionTracker sessionTracker) {
        UserCampaignTracker persistentTracker = new UserCampaignTracker();
        persistentTracker.setId(UUID.randomUUID().toString());
        persistentTracker.setUserId(sessionTracker.getUserId());
        persistentTracker.setCompanyId(sessionTracker.getCompanyId());
        persistentTracker.setCampaignId(sessionTracker.getCampaignId());
        persistentTracker.setWeekStartDate(sessionTracker.getWeekStartDate());
        
        // Set initial values from campaign or reasonable defaults
        persistentTracker.setRemainingWeeklyFrequency(5); // Should get from campaign
        persistentTracker.setRemainingDisplayCap(10);     // Should get from campaign
        
        return persistentTracker;
    }
    
    /**
     * Get session statistics
     */
    public int getActiveCampaignsCount(HttpSession session, String userId, String companyId) {
        return sessionTrackerRepository.countActiveCampaignsInSession(session.getId(), userId, companyId);
    }
}