package com.usbank.corp.dcr.api.entity;

import lombok.Data;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "user_campaign_tracker")
@Data
public class UserCampaignTracker {
    
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "company_id", nullable = false)
    private String companyId;
    
    @Column(name = "campaign_id", nullable = false)
    private String campaignId;
    
    @Column(name = "remaining_weekly_frequency", nullable = false)
    private Integer remainingWeeklyFrequency;
    
    @Column(name = "remaining_display_cap", nullable = false)
    private Integer remainingDisplayCap;
    
    @Column(name = "last_view_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastViewDate;
    
    @Column(name = "week_start_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date weekStartDate;
}