package com.usbank.corp.dcr.api.model;

import lombok.Data;

@Data
public class InsightClosureResponseDTO {
    
    private String campaignId;
    private Integer closureCount;
    private String action; // HIDDEN_UNTIL_NEXT_ELIGIBILITY, PROMPT_USER_PREFERENCE, CONSIDER_GLOBAL_OPTOUT
    private String message;
    private boolean requiresUserInput;
    private boolean previouslyClosed;
    private Date effectiveDate; // NEW: Show what date was used
    private Date nextEligibleDate; // NEW: Show when user can see again
}