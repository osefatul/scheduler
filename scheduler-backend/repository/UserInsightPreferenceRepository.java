package com.usbank.corp.dcr.api.repository;

import com.usbank.corp.dcr.api.entity.UserInsightPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserInsightPreferenceRepository extends JpaRepository<UserInsightPreference, String> {
    
    /**
     * Find preference for specific user, company, and campaign
     */
    Optional<UserInsightPreference> findByUserIdAndCompanyIdAndCampaignId(
        String userId, String companyId, String campaignId);
    
    /**
     * Find all campaigns preference for user and company (where campaignId is null)
     */
    Optional<UserInsightPreference> findByUserIdAndCompanyIdAndCampaignIdIsNull(
        String userId, String companyId);
    
    /**
     * Find all preferences for user and company
     */
    List<UserInsightPreference> findByUserIdAndCompanyId(String userId, String companyId);
    
    /**
     * Check if user has opted out of all campaigns
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
           "FROM UserInsightPreference p " +
           "WHERE p.userId = :userId AND p.companyId = :companyId " +
           "AND p.campaignId IS NULL AND p.preferenceType = 'OPTED_OUT_ALL'")
    boolean hasUserOptedOutOfAllCampaigns(@Param("userId") String userId, @Param("companyId") String companyId);
    
    /**
     * Check if user is in cooling period for all campaigns
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
           "FROM UserInsightPreference p " +
           "WHERE p.userId = :userId AND p.companyId = :companyId " +
           "AND p.campaignId IS NULL AND p.preferenceType = 'COOLING_PERIOD' " +
           "AND p.nextEligibleDate > :currentDate")
    boolean isUserInGlobalCoolingPeriod(@Param("userId") String userId, 
                                       @Param("companyId") String companyId, 
                                       @Param("currentDate") Date currentDate);
    
    /**
     * Find users eligible to return from cooling period
     */
    @Query("SELECT p FROM UserInsightPreference p " +
           "WHERE p.preferenceType = 'COOLING_PERIOD' " +
           "AND p.nextEligibleDate <= :currentDate")
    List<UserInsightPreference> findUsersEligibleToReturn(@Param("currentDate") Date currentDate);
}