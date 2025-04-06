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
     * Find campaigns with any of the provided statuses
     */
    List<CampaignMapping> findByStatusIn(List<String> statuses);

    boolean existsById(String id);
    
    /**
     * Get all eligible campaigns for a given date and company
     * Uses join to campaign_company_mapping table for accurate company matching
     * 
     * @param currentDate Current date in yyyy-MM-dd format
     * @param companyId Company identifier
     * @return List of eligible campaigns
     */
    @Query(value = "SELECT c.* FROM [dbo].[campaigns_dev_rotation1] c "
            + "INNER JOIN [dbo].[campaign_company_mapping] m ON c.id = m.campaign_id "
            + "WHERE c.[start_date] <= :current_date AND c.[end_date] >= :current_date "
            + "AND (c.visibility is NULL OR c.visibility != 'COMPLETED') "
            + "AND m.company_id = :company_id "
            + "AND (c.status = 'ACTIVE' OR c.status = 'SCHEDULED' OR c.status = 'INPROGRESS') "
            + "ORDER BY c.created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> getEligibleCampaignsForCompany(
            @Param("current_date") String currentDate,
            @Param("company_id") String companyId);
    
    /**
     * Get all campaigns eligible for rotation within the next week
     * Uses join to campaign_company_mapping table
     * 
     * @param companyId Company identifier
     * @return List of eligible campaigns
     */
    @Query(value = "SELECT c.* FROM [dbo].[campaigns_dev_rotation1] c "
            + "INNER JOIN [dbo].[campaign_company_mapping] m ON c.id = m.campaign_id "
            + "WHERE c.start_date <= DATEADD(day, +7, GETDATE()) "
            + "AND c.end_date >= GETDATE() "
            + "AND (c.visibility is NULL OR c.visibility != 'COMPLETED') "
            + "AND m.company_id = :company_id "
            + "AND (c.status = 'ACTIVE' OR c.status = 'SCHEDULED' OR c.status = 'INPROGRESS') "
            + "ORDER BY c.created_date ASC", 
            nativeQuery = true)
            List<CampaignMapping> getEligibleCampaignsForRotations();
    List<CampaignMapping> getEligibleCampaignsForRotations(@Param("company_id") String companyId);
    
    /**
     * Legacy method for getting eligible campaigns
     * Maintained for backward compatibility
     */
    @Query(value = "SELECT * FROM [dbo].[campaigns_dev_rotation1] WHERE "
            + "([end_date] >= :end_date) AND (visibility is NULL OR visibility!='COMPLETED') "
            + " AND company_names LIKE %:company% order by "
            + "created_date asc", nativeQuery = true)
    List<CampaignMapping> getEligibleCampaignsBasedonRequestDate(
            @Param("end_date") String end_date,@Param("company") String company);
    
    /**
     * Find campaigns by company with active status
     */
    @Query(value = "SELECT c.* FROM [dbo].[campaigns_dev_rotation1] c "
            + "INNER JOIN [dbo].[campaign_company_mapping] m ON c.id = m.campaign_id "
            + "WHERE m.company_id = :company_id "
            + "AND (c.status = 'ACTIVE' OR c.status = 'SCHEDULED' OR c.status = 'INPROGRESS') "
            + "AND (c.visibility is NULL OR c.visibility != 'COMPLETED') "
            + "ORDER BY c.created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> findActiveCampaignsByCompany(@Param("company_id") String companyId);
    
    /**
     * Find campaigns active in a specific week
     */
    @Query(value = "SELECT * FROM [dbo].[campaigns_dev_rotation1] WHERE "
            + "start_date <= :week_end "
            + "AND end_date >= :week_start "
            + "AND (status = 'ACTIVE' OR status = 'SCHEDULED' OR status = 'INPROGRESS') "
            + "ORDER BY created_date ASC", 
            nativeQuery = true)
    List<CampaignMapping> findCampaignsActiveInWeek(
            @Param("week_start") Date weekStart,
            @Param("week_end") Date weekEnd);
    
    /**
     * Reset weekly frequency for campaigns
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE [dbo].[campaigns_dev_rotation1] SET "
            + "frequency_per_week = original_frequency_per_week, "
            + "rotation_status = NULL "
            + "WHERE id IN :campaignIds "
            + "AND original_frequency_per_week IS NOT NULL", 
            nativeQuery = true)
    int resetWeeklyFrequency(@Param("campaignIds") List<String> campaignIds);
    
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
}