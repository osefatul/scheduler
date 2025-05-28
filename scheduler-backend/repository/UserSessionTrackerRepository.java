package com.usbank.corp.dcr.api.repository;

import com.usbank.corp.dcr.api.entity.UserSessionTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionTrackerRepository extends JpaRepository<UserSessionTracker, String> {
    
    /**
     * Find active session tracker for user, company, and campaign
     */
    Optional<UserSessionTracker> findBySessionIdAndUserIdAndCompanyIdAndCampaignIdAndSessionActiveTrue(
        String sessionId, String userId, String companyId, String campaignId);
    
    /**
     * Find all active session trackers for a session
     */
    List<UserSessionTracker> findBySessionIdAndSessionActiveTrue(String sessionId);
    
    /**
     * Find all active session trackers for user and company
     */
    List<UserSessionTracker> findByUserIdAndCompanyIdAndSessionActiveTrue(String userId, String companyId);
    
    /**
     * Mark session as inactive
     */
    @Modifying
    @Transactional
    @Query("UPDATE UserSessionTracker s SET s.sessionActive = false " +
           "WHERE s.sessionId = :sessionId")
    int deactivateSession(@Param("sessionId") String sessionId);
    
    /**
     * Count active campaigns in session for user
     */
    @Query("SELECT COUNT(s) FROM UserSessionTracker s " +
           "WHERE s.sessionId = :sessionId AND s.userId = :userId " +
           "AND s.companyId = :companyId AND s.sessionActive = true AND s.viewedInSession = true")
    int countActiveCampaignsInSession(@Param("sessionId") String sessionId, 
                                     @Param("userId") String userId, 
                                     @Param("companyId") String companyId);
}