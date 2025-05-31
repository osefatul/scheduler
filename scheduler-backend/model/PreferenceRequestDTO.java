package com.usbank.corp.dcr.api.model;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class PreferenceRequestDTO {
    @NotBlank
    private String userId;
    
    @NotBlank
    private String companyId;
    
    @NotBlank
    private String campaignId;
    
    @NotNull
    private Boolean wantsToSee; // true = want to see, false = don't want to see
    
    private String reason;
    
    @NotNull
    private Boolean isGlobalResponse; // true = response to global prompt, false = campaign-specific
    
    private String preferenceDate; // Optional date parameter for testing
}