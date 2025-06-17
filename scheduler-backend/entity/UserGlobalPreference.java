package com.usbank.corp.dcr.api.entity;

import javax.persistence.*;
import java.util.Date;
import lombok.Data;

@Entity
@Table(name = "user_global_preference")
@Data
public class UserGlobalPreference {
    
    @Id
    private String id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;
    
    @Column(name = "insights_enabled", nullable = false)
    private Boolean insightsEnabled = true;
    
    @Column(name = "opt_out_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date optOutDate;
    
    @Column(name = "opt_out_reason", length = 1000)
    private String optOutReason;
    
    @Column(name = "created_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;
    
    @Column(name = "updated_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedDate;


    // NEW FIELDS for global wait period
    @Column(name = "global_wait_until_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date globalWaitUntilDate;
    
    @Column(name = "global_wait_reason")
    private String globalWaitReason;

    
    @PrePersist
    protected void onCreate() {
        createdDate = new Date();
        updatedDate = new Date();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedDate = new Date();
    }

        // Manual getters and setters for new fields (in case Lombok doesn't pick them up)
        public Date getGlobalWaitUntilDate() {
            return globalWaitUntilDate;
        }
        
        public void setGlobalWaitUntilDate(Date globalWaitUntilDate) {
            this.globalWaitUntilDate = globalWaitUntilDate;
        }
        
        public String getGlobalWaitReason() {
            return globalWaitReason;
        }
        
        public void setGlobalWaitReason(String globalWaitReason) {
            this.globalWaitReason = globalWaitReason;
        }
}