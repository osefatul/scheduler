package com.usbank.corp.dcr.api.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing the many-to-many relationship between campaigns and companies
 */
@Entity
@Table(name = "campaign_company_mapping")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignCompanyMapping {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "campaign_id", nullable = false)
    private String campaignId;
    
    @Column(name = "company_id", nullable = false)
    private String companyId;
}

package com.usbank.corp.dcr.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.usbank.corp.dcr.api.entity.CampaignCompanyMapping;

/**
 * Repository for managing campaign to company mappings
 */
@Repository
public interface CampaignCompanyRepository extends JpaRepository<CampaignCompanyMapping, String> {
    
    /**
     * Find all mappings for a specific campaign
     */
    List<CampaignCompanyMapping> findByCampaignId(String campaignId);
    
    /**
     * Find all mappings for a specific company
     */
    List<CampaignCompanyMapping> findByCompanyId(String companyId);
    
    /**
     * Check if a mapping exists for a campaign and company
     */
    boolean existsByCampaignIdAndCompanyId(String campaignId, String companyId);
    
    /**
     * Delete all mappings for a campaign
     */
    void deleteByCampaignId(String campaignId);
}