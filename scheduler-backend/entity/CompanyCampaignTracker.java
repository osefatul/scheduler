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
         * Check if this tracker represents a campaign that's been viewed this week
         * 
         * @param weekStartDate Start date of the current week
         * @return true if viewed this week
         */
        public boolean isViewedThisWeek(Date weekStartDate) {
            // Must have been reset in current week
            if (lastWeekReset == null || lastWeekReset.before(weekStartDate)) {
                return false;
            }
            
            // Must have been updated in current week
            if (lastUpdated == null || lastUpdated.before(weekStartDate)) {
                return false;
            }
            
            // Check if frequency has been used at all this week
            return originalWeeklyFrequency != null && 
                   remainingWeeklyFrequency != null &&
                   remainingWeeklyFrequency < originalWeeklyFrequency;
        }
        
        /**
         * Check if this tracker is eligible for display
         * Must have both weekly frequency and display cap available
         * 
         * @return true if eligible, false otherwise
         */
        public boolean isEligibleForDisplay() {
            return remainingWeeklyFrequency != null && remainingWeeklyFrequency > 0 &&
                   remainingDisplayCap != null && remainingDisplayCap > 0;
        }
        
        /**
         * Apply one view to this tracker
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
            remainingWeeklyFrequency = Math.max(0, remainingWeeklyFrequency - 1);
            remainingDisplayCap = Math.max(0, remainingDisplayCap - 1);
            lastUpdated = currentDate;
            
            return true;
        }
    }