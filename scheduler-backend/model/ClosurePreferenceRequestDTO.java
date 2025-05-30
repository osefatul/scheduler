package com.usbank.corp.dcr.api.model;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class ClosurePreferenceRequestDTO {
    
    @NotNull(message = "User ID is required")
    private String userId;
    
    @NotNull(message = "Company ID is required")
    private String companyId;
    
    @NotNull(message = "Campaign ID is required")
    private String campaignId;
    
    @NotNull(message = "User preference is required")
    private Boolean wantsToSeeAgain;
    
    private String reason; // Free text reason from frontend
}