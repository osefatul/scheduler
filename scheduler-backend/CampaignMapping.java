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
import javax.persistence.Transient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaigns_dev_rotation1")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, name = "banner_id")
    private String bannerId;
    
    @Column(nullable = false, name = "insight_type")
    private String insightType;
    
    @Column(nullable = false, name = "insight_sub_type")
    private String insightSubType;
    
    @Column(nullable = false, name = "insight")
    private String insight;
    
    @Column(name = "eligible_companies")
    private Integer eligibleCompanies;
    
    @Column(name = "eligible_users")
    private Integer eligibleUsers;
    
    @Column(name = "start_date")
    @Temporal(TemporalType.DATE)
    private Date startDate;
    
    @Column(name = "end_date")
    @Temporal(TemporalType.DATE)
    private Date endDate;
    
    @Column(name = "frequency_per_week")
    private Integer frequencyPerWeek;
    
    @Column(name = "original_frequency_per_week")
    private Integer orginalFrequencyPerWeek;
    
    @Column(name = "display_capping")
    private Integer displayCapping;
    
    @Column(name = "display_location")
    private String displayLocation;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "created_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;
    
    @Column(name = "updated_date") 
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedDate;
    
    @Column(name = "visibility")
    private String visibility; // VISIBLE, COMPLETED
    
    @Column(name = "start_week_of_requested_date")
    @Temporal(TemporalType.DATE)
    private Date start_week_of_requested_date;
    
    @Column(name = "requested_date")
    @Temporal(TemporalType.DATE)
    private Date requested_date;
    
    @Column(name = "status")
    private String status; // DRAFT, SCHEDULED, ACTIVE, COMPLETED, CANCELLED, INPROGRESS
    
    @Column(name = "rotation_status")
    private String rotation_status; // null (show first), ROTATION_POSITION_X, ROTATED_RECENTLY, etc.
    
    @Column(name = "campaign_steps")
    private String campaignSteps; // MAP_INSIGHT, CAMPAIGN_DURATION, REVIEW, SUBMIT
    
    /**
     * Check if the campaign is eligible for display
     * 
     * @param currentDate Current date
     * @return true if eligible, false otherwise
     */
    @Transient
    public boolean isEligibleForDisplay(Date currentDate) {
        // Must be within date range
        if (startDate != null && endDate != null) {
            if (currentDate.before(startDate) || currentDate.after(endDate)) {
                return false;
            }
        }
        
        // Must have available display quota
        if (displayCapping != null && displayCapping <= 0) {
            return false;
        }
        
        // Must have available weekly frequency
        if (frequencyPerWeek != null && frequencyPerWeek <= 0) {
            return false;
        }
        
        // Must not be completed
        if ("COMPLETED".equals(visibility)) {
            return false;
        }
        
        // Must be active or scheduled
        if (!"ACTIVE".equals(status) && !"SCHEDULED".equals(status) && !"INPROGRESS".equals(status)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if weekly frequency needs to be reset
     * 
     * @param weekStartDate Start date of the current week
     * @return true if reset needed, false otherwise
     */
    @Transient
    public boolean needsFrequencyReset(Date weekStartDate) {
        if (updatedDate == null || orginalFrequencyPerWeek == null) {
            return false;
        }
        
        // If last update was before this week's start and frequency is not at original value
        return updatedDate.before(weekStartDate) && !frequencyPerWeek.equals(orginalFrequencyPerWeek);
    }
    
    /**
     * Apply one view to this campaign
     * Updates frequency and display capping counters
     * 
     * @param currentDate Current date
     * @return true if successful, false if not eligible
     */
    @Transient
    public boolean applyView(Date currentDate) {
        if (!isEligibleForDisplay(currentDate)) {
            return false;
        }
        
        // Decrement counters
        frequencyPerWeek--;
        displayCapping--;
        
        // Record original frequency if not already set
        if (orginalFrequencyPerWeek == null) {
            orginalFrequencyPerWeek = frequencyPerWeek + 1;
        }
        
        // Update timestamps
        updatedDate = currentDate;
        requested_date = currentDate;
        
        // Update visibility status
        visibility = displayCapping <= 0 ? "COMPLETED" : "VISIBLE";
        
        // Update rotation status if weekly frequency is exhausted
        if (frequencyPerWeek <= 0 && displayCapping > 0) {
            rotation_status = "ROTATED_RECENTLY";
        } else {
            rotation_status = null;
        }
        
        return true;
    }
}