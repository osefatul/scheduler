package com.usbank.corp.dcr.api.model;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class GlobalOptOutRequestDTO {
    
    @NotNull(message = "User ID is required")
    private String userId;
    
    @NotNull(message = "Opt-out preference is required")
    private Boolean optOut;
    
    private String reason; // Free text reason from frontend
}