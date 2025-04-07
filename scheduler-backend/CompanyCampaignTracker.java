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

/**
 * Entity to track campaign usage per company
 * This enables company-specific frequency and capping management
 */
@Entity
@Table(name = "company_campaign_tracker")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCampaignTracker {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "company_id", nullable = false)
    private String companyId;
    
    @Column(name = "campaign_id", nullable = false)
    private String campaignId;
    
    @Column(name = "remaining_weekly_frequency")
    private Integer remainingWeeklyFrequency;
    
    @Column(name = "original_weekly_frequency")
    private Integer originalWeeklyFrequency;
    
    @Column(name = "remaining_display_cap")
    private Integer remainingDisplayCap;
    
    @Column(name = "last_updated")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUpdated;
    
    @Column(name = "last_week_reset")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastWeekReset;
    
    @Column(name = "rotation_status")
    private String rotationStatus;
    
    /**
     * Check if weekly frequency needs to be reset
     * 
     * @param weekStartDate Start date of the current week
     * @return true if reset needed, false otherwise
     */
    public boolean needsFrequencyReset(Date weekStartDate) {
        if (lastWeekReset == null || originalWeeklyFrequency == null) {
            return false;
        }
        
        // If last reset was before this week's start
        return lastWeekReset.before(weekStartDate);
    }
    
    /**
     * Apply one view to this company-campaign tracker
     * Updates frequency and display capping counters
     * 
     * @param currentDate Current date
     * @return true if successful, false if not eligible
     */
    public boolean applyView(Date currentDate) {
        if (!isEligibleForDisplay()) {
            return false;
        }
        
        // Decrement counters
        remainingWeeklyFrequency--;
        remainingDisplayCap--;
        
        // Update timestamps
        lastUpdated = currentDate;
        
        // Update rotation status if weekly frequency is exhausted
        if (remainingWeeklyFrequency <= 0 && remainingDisplayCap > 0) {
            rotationStatus = "ROTATED_RECENTLY";
        } else {
            rotationStatus = null;
        }
        
        return true;
    }
    
    /**
     * Reset weekly frequency back to original value
     * 
     * @param weekStartDate Current week start date
     */
    public void resetWeeklyFrequency(Date weekStartDate) {
        if (originalWeeklyFrequency != null) {
            remainingWeeklyFrequency = originalWeeklyFrequency;
            rotationStatus = null;
            lastWeekReset = weekStartDate;
        }
    }
    
    /**
     * Check if this company-campaign is eligible for display
     * 
     * @return true if eligible, false otherwise
     */
    public boolean isEligibleForDisplay() {
        // Must have available weekly frequency
        if (remainingWeeklyFrequency == null || remainingWeeklyFrequency <= 0) {
            return false;
        }
        
        // Must have available display quota
        if (remainingDisplayCap == null || remainingDisplayCap <= 0) {
            return false;
        }
        
        return true;
    }
}