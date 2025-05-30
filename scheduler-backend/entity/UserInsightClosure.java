package com.usbank.corp.dcr.api.entity;

import javax.persistence.*;
import java.util.Date;
import lombok.Data;

@Entity
@Table(name = "user_insight_closure")
@Data
public class UserInsightClosure {
    
    @Id
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "company_id", nullable = false)
    private String companyId;
    
    @Column(name = "campaign_id", nullable = false)
    private String campaignId;
    
    @Column(name = "closure_count", nullable = false)
    private Integer closureCount = 0;
    
    @Column(name = "permanently_closed", nullable = false)
    private Boolean permanentlyClosed = false;
    
    @Column(name = "closure_reason", length = 1000)
    private String closureReason;
    
    @Column(name = "first_closure_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date firstClosureDate;
    
    @Column(name = "last_closure_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastClosureDate;
    
    @Column(name = "next_eligible_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date nextEligibleDate;
    
    @Column(name = "opt_out_all_insights", nullable = false)
    private Boolean optOutAllInsights = false;
    
    @Column(name = "created_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;
    
    @Column(name = "updated_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedDate;
    
    @PrePersist
    protected void onCreate() {
        createdDate = new Date();
        updatedDate = new Date();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedDate = new Date();
    }
}