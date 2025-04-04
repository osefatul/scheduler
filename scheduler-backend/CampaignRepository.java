package com.usbank.corp.dcr.api.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Find campaigns with any of the provided statuses
     */
    List<CampaignMapping> findByStatusIn(List<String> statuses);
    
    /**
     * Get all eligible campaigns for a given date and company
     * Campaigns are eligible if:
     * 1. Current date is between start and end date
     * 2. Not marked as COMPLETED for visibility
     * 3. Company name matches
     * 4. Status is ACTIVE or SCHEDULED
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
     * Find campaigns that need weekly frequency reset
     */
    @Query(value = "SELECT * FROM [dbo].[campaigns_dev_rotation1] WHERE "
            + "updated_date < :week_start_date "
            + "AND end_date >= GETDATE() "
            + "AND (visibility is NULL OR visibility != 'COMPLETED') "
            + "AND frequency_per_week != original_frequency_per_week "
            + "AND original_frequency_per_week IS NOT NULL "
            + "ORDER BY created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> findCampaignsNeedingFrequencyReset(
            @Param("week_start_date") Date weekStartDate);
    
    /**
     * Find campaigns by company with active status
     */
    @Query(value = "SELECT * FROM [dbo].[campaigns_dev_rotation1] WHERE "
            + "company_names LIKE %:company% "
            + "AND (status = 'ACTIVE' OR status = 'SCHEDULED') "
            + "AND (visibility is NULL OR visibility != 'COMPLETED') "
            + "ORDER BY created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> findActiveCampaignsByCompany(@Param("company") String company);
    
    /**
     * Find campaigns active in a specific week
     */
    @Query(value = "SELECT * FROM [dbo].[campaigns_dev_rotation1] WHERE "
            + "start_date <= :week_end "
            + "AND end_date >= :week_start "
            + "AND (status = 'ACTIVE' OR status = 'SCHEDULED') "
            + "ORDER BY created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> findCampaignsActiveInWeek(
            @Param("week_start") Date weekStart,
            @Param("week_end") Date weekEnd);
}