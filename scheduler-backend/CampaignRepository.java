package com.usbank.corp.dcr.api.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.usbank.corp.dcr.api.entity.CampaignMapping;

@Repository
public interface CampaignRepository extends JpaRepository<CampaignMapping, String> {
    
    /**
     * Find campaigns by created user
     */
    List<CampaignMapping> findByCreatedBy(String createdBy);
    
    /**
     * Find campaigns by status
     */
    List<CampaignMapping> findByStatus(String status);
    
    /**
     * Get all eligible campaigns for a given date and company
     * Campaigns are eligible if:
     * 1. Current date is between start and end date
     * 2. Not marked as COMPLETED for visibility
     * 3. Company name matches
     * 
     * @param currentDate Current date in yyyy-MM-dd format
     * @param company Company identifier
     * @return List of eligible campaigns
     */
    @Query(value = "SELECT * FROM [dbo].[campaigns_dev_rotation1] WHERE "
            + "([start_date] <= :current_date AND [end_date] >= :current_date) "
            + "AND (visibility is NULL OR visibility != 'COMPLETED') "
            + "AND company_names LIKE %:company% "
            + "AND (status = 'ACTIVE' OR status = 'SCHEDULED') "
            + "ORDER BY created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> getEligibleCampaignsBasedonRequestDate(
            @Param("current_date") String currentDate,
            @Param("company") String company);
    
    /**
     * Get all campaigns eligible for rotation within the next week
     * Used for administrative purposes or pre-fetching
     */
    @Query(value = "SELECT * FROM [dbo].[campaigns_dev_rotation1] WHERE "
            + "start_date <= DATEADD(day, +7, GETDATE()) "
            + "AND end_date >= GETDATE() "
            + "AND (visibility is NULL OR visibility != 'COMPLETED') "
            + "AND (status = 'ACTIVE' OR status = 'SCHEDULED') "
            + "ORDER BY created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> getEligibleCampaignsForRotations();
    
    /**
     * Get campaigns that need frequency reset for a new week
     * 
     * @param weekStartDate Start date of the current week
     * @return List of campaigns needing reset
     */
    @Query(value = "SELECT * FROM [dbo].[campaigns_dev_rotation1] WHERE "
            + "updated_date < :week_start_date "
            + "AND end_date >= GETDATE() "
            + "AND (visibility is NULL OR visibility != 'COMPLETED') "
            + "AND frequency_per_week != original_frequency_per_week "
            + "ORDER BY created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> getCampaignsNeedingFrequencyReset(
            @Param("week_start_date") Date weekStartDate);
    
    /**
     * Find campaigns by rotation status
     */
    List<CampaignMapping> findByRotation_status(String rotationStatus);
    
    /**
     * Find campaigns active during a specific week
     * 
     * @param weekStartDate Start date of the week
     * @param weekEndDate End date of the week
     * @return List of active campaigns
     */
    @Query(value = "SELECT * FROM [dbo].[campaigns_dev_rotation1] WHERE "
            + "start_date <= :week_end_date AND end_date >= :week_start_date "
            + "AND (status = 'ACTIVE' OR status = 'SCHEDULED') "
            + "ORDER BY created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> findCampaignsActiveInWeek(
            @Param("week_start_date") Date weekStartDate,
            @Param("week_end_date") Date weekEndDate);
    
    /**
     * Reset frequency for campaigns at the start of a new week
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE [dbo].[campaigns_dev_rotation1] SET "
            + "frequency_per_week = original_frequency_per_week "
            + "WHERE id IN :campaignIds", 
            nativeQuery = true)
    int resetWeeklyFrequency(@Param("campaignIds") List<String> campaignIds);
    
    /**
     * Get campaigns eligible for a specific user
     * Considers display capping against user view history
     * 
     * @param currentDate Current date
     * @param company Company identifier
     * @param userId User identifier
     * @return List of eligible campaigns
     */
    @Query(value = "SELECT c.* FROM [dbo].[campaigns_dev_rotation1] c "
            + "LEFT JOIN ("
            + "    SELECT campaign_id, COUNT(*) as view_count "
            + "    FROM [dbo].[user_campaign_history] "
            + "    WHERE user_id = :userId "
            + "    GROUP BY campaign_id"
            + ") h ON c.id = h.campaign_id "
            + "WHERE (c.start_date <= :current_date AND c.end_date >= :current_date) "
            + "AND (c.visibility is NULL OR c.visibility != 'COMPLETED') "
            + "AND c.company_names LIKE %:company% "
            + "AND (c.status = 'ACTIVE' OR c.status = 'SCHEDULED') "
            + "AND (h.view_count IS NULL OR h.view_count < c.display_capping) "
            + "ORDER BY c.created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> getEligibleCampaignsForUser(
            @Param("current_date") Date currentDate,
            @Param("company") String company,
            @Param("userId") String userId);
}