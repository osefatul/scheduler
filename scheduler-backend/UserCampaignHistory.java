package com.usbank.corp.dcr.api.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_campaign_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCampaignHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, name = "user_id")
    private String userId;
    
    @Column(nullable = false, name = "campaign_id")
    private String campaignId;
    
    @Column(name = "view_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date viewDate;
    
    @Column(name = "has_clicked_learn_more")
    private Boolean hasClickedLearnMore = false;
    
    @Column(name = "has_submitted_form")
    private Boolean hasSubmittedForm = false;
}

package com.usbank.corp.dcr.api.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.usbank.corp.dcr.api.entity.UserCampaignHistory;

@Repository
public interface UserCampaignHistoryRepository extends JpaRepository<UserCampaignHistory, String> {
    
    /**
     * Find all history records for a user
     * 
     * @param userId User ID
     * @return List of history records
     */
    List<UserCampaignHistory> findAllByUserId(String userId);
    
    /**
     * Find a specific campaign history for a user
     * 
     * @param campaignId Campaign ID
     * @param userId User ID
     * @return Optional containing history if found
     */
    Optional<UserCampaignHistory> findByCampaignIdAndUserId(String campaignId, String userId);
    
    /**
     * Find the most recent campaign history for a user after a given date
     * 
     * @param userId User ID
     * @param startDate Start date to search from
     * @return Optional containing the most recent history if found
     */
    @Query("SELECT h FROM UserCampaignHistory h WHERE h.userId = :userId AND h.viewDate >= :startDate " +
           "ORDER BY h.viewDate DESC")
    Optional<UserCampaignHistory> findRecentUserHistory(@Param("userId") String userId, 
                                                      @Param("startDate") Date startDate);
    
    /**
     * Count how many times a user has viewed a specific campaign
     * 
     * @param userId User ID
     * @param campaignId Campaign ID
     * @return Count of views
     */
    @Query("SELECT COUNT(h) FROM UserCampaignHistory h WHERE h.userId = :userId AND h.campaignId = :campaignId")
    long countByCampaignIdAndUserId(@Param("userId") String userId, @Param("campaignId") String campaignId);
    
    /**
     * Find if a user has viewed any campaign in a given date range
     * 
     * @param userId User ID
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return Optional containing history if found
     */
    @Query("SELECT h FROM UserCampaignHistory h WHERE h.userId = :userId " +
          "AND h.viewDate >= :startDate AND h.viewDate <= :endDate " +
          "ORDER BY h.viewDate DESC")
    Optional<UserCampaignHistory> findUserHistoryInDateRange(@Param("userId") String userId,
                                                          @Param("startDate") Date startDate,
                                                          @Param("endDate") Date endDate);
}