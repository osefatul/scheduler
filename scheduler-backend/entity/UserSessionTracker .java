package com.usbank.corp.dcr.api.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "user_session_tracker")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionTracker {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "session_id", nullable = false)
    private String sessionId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "company_id", nullable = false)
    private String companyId;
    
    @Column(name = "campaign_id", nullable = false)
    private String campaignId;
    
    @Column(name = "viewed_in_session", nullable = false)
    private Boolean viewedInSession = false;
    
    @Column(name = "session_start_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date sessionStartTime;
    
    @Column(name = "last_activity_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastActivityTime;
    
    @Column(name = "session_active", nullable = false)
    private Boolean sessionActive = true;
    
    @Column(name = "week_start_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date weekStartDate;
    
    /**
     * Mark campaign as viewed in this session
     */
    public void markAsViewed() {
        this.viewedInSession = true;
        this.lastActivityTime = new Date();
    }
    
    /**
     * Update activity timestamp without changing view status
     */
    public void updateActivity() {
        this.lastActivityTime = new Date();
    }
}