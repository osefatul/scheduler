// WarmLeadTrackingRepository.java
package com.ubank.corp.dcr.api.repository;

import com.ubank.corp.dcr.api.entity.WarmLeadTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WarmLeadTrackingRepository extends JpaRepository<WarmLeadTracking, String> {
    
    Optional<WarmLeadTracking> findByUserIdentifierAndCampaignIdAndInsightSubType(
        String userIdentifier, String campaignId, String insightSubType);
    
    @Query("SELECT w FROM WarmLeadTracking w WHERE w.userIdentifier = :userIdentifier " +
           "AND w.campaignId = :campaignId AND w.insightSubType = :insightSubType " +
           "AND w.isConvertedToHot = false")
    Optional<WarmLeadTracking> findActiveWarmLead(
        @Param("userIdentifier") String userIdentifier,
        @Param("campaignId") String campaignId, 
        @Param("insightSubType") String insightSubType);
}

