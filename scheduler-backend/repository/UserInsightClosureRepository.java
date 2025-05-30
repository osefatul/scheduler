package com.usbank.corp.dcr.api.repository;

import com.usbank.corp.dcr.api.entity.UserInsightClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserInsightClosureRepository extends JpaRepository<UserInsightClosure, String> {
    
    /**
     * Find closure record for specific user, company, and campaign
     */
    Optional<UserInsightClosure> findByUserIdAndCompanyIdAndCampaignId(
            String userId, String companyId, String campaignId);
    
    /**
     * Find all closures for a user in a company
     */
    List<UserInsightClosure> findByUserIdAndCompanyId(String userId, String companyId);
    
    /**
     * Find all closures for a user
     */
    List<UserInsightClosure> findByUserId(String userId);
    
    /**
     * Find all closures for a campaign
     */
    List<UserInsightClosure> findByCampaignId(String campaignId);
    
    /**
     * Find all permanently closed campaigns for a user
     */
    @Query("SELECT c FROM UserInsightClosure c WHERE c.userId = :userId " +
           "AND c.permanentlyClosed = true")
    List<UserInsightClosure> findPermanentlyClosedByUserId(@Param("userId") String userId);
    
    /**
     * Find closures that are eligible after a certain date
     */
    @Query("SELECT c FROM UserInsightClosure c WHERE c.userId = :userId " +
           "AND c.companyId = :companyId " +
           "AND c.permanentlyClosed = false " +
           "AND (c.nextEligibleDate IS NULL OR c.nextEligibleDate <= :currentDate)")
    List<UserInsightClosure> findEligibleClosures(
            @Param("userId") String userId, 
            @Param("companyId") String companyId,
            @Param("currentDate") Date currentDate);
    
    /**
     * Find users who have opted out of all insights
     */
    @Query("SELECT c FROM UserInsightClosure c WHERE c.userId = :userId " +
           "AND c.optOutAllInsights = true")
    List<UserInsightClosure> findOptedOutUsers(@Param("userId") String userId);
    
    /**
     * Check if user has opted out globally
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
           "FROM UserInsightClosure c WHERE c.userId = :userId " +
           "AND c.optOutAllInsights = true")
    boolean hasUserOptedOutGlobally(@Param("userId") String userId);
    
    /**
     * Find campaigns currently in wait period for a user
     */
    @Query("SELECT c FROM UserInsightClosure c WHERE c.userId = :userId " +
           "AND c.companyId = :companyId " +
           "AND c.permanentlyClosed = false " +
           "AND c.nextEligibleDate > :currentDate")
    List<UserInsightClosure> findCampaignsInWaitPeriod(
            @Param("userId") String userId, 
            @Param("companyId") String companyId,
            @Param("currentDate") Date currentDate);
    
    /**
     * Count total closures for analytics
     */
    @Query("SELECT COUNT(c) FROM UserInsightClosure c " +
           "WHERE c.campaignId = :campaignId " +
           "AND c.closureCount >= :minClosureCount")
    long countClosuresByCampaign(
            @Param("campaignId") String campaignId,
            @Param("minClosureCount") Integer minClosureCount);
}