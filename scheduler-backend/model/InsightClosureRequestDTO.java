package com.usbank.corp.dcr.api.model;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class InsightClosureRequestDTO {
    
    @NotNull(message = "User ID is required")
    private String userId;
    
    @NotNull(message = "Company ID is required")
    private String companyId;
    
    @NotNull(message = "Campaign ID is required")
    private String campaignId;
    
    private String action; // CLOSE, RESPOND_TO_PROMPT, GLOBAL_OPTOUT

    // NEW: Optional date parameter for testing
    private String closureDate; // Format: yyyy-MM-dd or yyyyMMdd
}