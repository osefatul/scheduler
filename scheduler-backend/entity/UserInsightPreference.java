package com.usbank.corp.dcr.api.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "user_insight_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInsightPreference {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "company_id", nullable = false)
    private String companyId;
    
    @Column(name = "campaign_id")
    private String campaignId; // null means all campaigns
    
    @Column(name = "preference_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private PreferenceType preferenceType;
    
    @Column(name = "closure_count")
    private Integer closureCount = 0;
    
    @Column(name = "opt_out_reason")
    private String optOutReason;
    
    @Column(name = "opt_out_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date optOutDate;
    
    @Column(name = "next_eligible_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date nextEligibleDate;
    
    @Column(name = "created_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;
    
    @Column(name = "updated_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedDate;
    
    public enum PreferenceType {
        CLOSED_ONCE,        // User closed insight once
        CLOSED_TWICE,       // User closed insight twice, asked about preference
        OPTED_OUT_CAMPAIGN, // User opted out of specific campaign
        OPTED_OUT_ALL,      // User opted out of all campaigns
        COOLING_PERIOD      // User in 1-month cooling period
    }
    
    /**
     * Check if user is currently in cooling period
     */
    public boolean isInCoolingPeriod() {
        if (nextEligibleDate == null) {
            return false;
        }
        return new Date().before(nextEligibleDate);
    }
    
    /**
     * Check if user is eligible to see campaigns
     */
    public boolean isEligibleForCampaigns() {
        if (preferenceType == PreferenceType.OPTED_OUT_ALL) {
            return false;
        }
        return !isInCoolingPeriod();
    }
}