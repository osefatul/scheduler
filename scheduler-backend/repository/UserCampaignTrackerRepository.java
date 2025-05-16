package com.usbank.corp.dcr.api.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.usbank.corp.dcr.api.entity.UserCampaignTracker;

@Repository
public interface UserCampaignTrackerRepository extends JpaRepository<UserCampaignTracker, String> {
    
    Optional<UserCampaignTracker> findByUserIdAndCompanyIdAndCampaignIdAndWeekStartDate(
        String userId, String companyId, String campaignId, Date weekStartDate);

    List<UserCampaignTracker> findByUserIdAndCompanyIdAndCampaignId(
    String userId, String companyId, String campaignId);
    
    List<UserCampaignTracker> findByUserIdAndCompanyIdAndWeekStartDate(
        String userId, String companyId, Date weekStartDate);
    
    @Query("SELECT t FROM UserCampaignTracker t WHERE t.userId = :userId AND t.companyId = :companyId " +
           "ORDER BY t.lastViewDate DESC")
    List<UserCampaignTracker> findRecentByUserIdAndCompanyId(
        @Param("userId") String userId, @Param("companyId") String companyId);
    
    List<UserCampaignTracker> findByCampaignId(String campaignId);
}