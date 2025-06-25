package com.ubank.corp.dcr.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "warm_lead_tracking")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WarmLeadTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId; // Could be userId, sessionId, or browser fingerprint

    @Column(name = "campaign_id", nullable = false)
    private String campaignId;

    @Column(name = "insight_sub_type", nullable = false)
    private String insightSubType; // Product type (FX, VirtualPay, etc.)

    @Column(name = "visit_count", nullable = false)
    private Integer visitCount = 1;

    @Column(name = "first_visit_date", nullable = false)
    private LocalDateTime firstVisitDate;

    @Column(name = "last_visit_date", nullable = false)
    private LocalDateTime lastVisitDate;

    @Column(name = "user_agent", nullable = true)
    private String userAgent; // For better tracking

    @Column(name = "referrer_url", nullable = true)
    private String referrerUrl;

    @Column(name = "is_converted_to_hot", nullable = false)
    private Boolean isConvertedToHot = false;

    @Column(name = "converted_to_hot_date", nullable = true)
    private LocalDateTime convertedToHotDate;

    // Helper method to increment visit count
    public void incrementVisitCount() {
        this.visitCount++;
        this.lastVisitDate = LocalDateTime.now();
    }

    // Helper method to mark as converted to hot lead
    public void markAsConvertedToHot() {
        this.isConvertedToHot = true;
        this.convertedToHotDate = LocalDateTime.now();
    }
}