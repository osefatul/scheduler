package com.usbank.corp.dcr.api.model;

import lombok.Data;

@Data
public class StopAllRequest {
    private String userId;
    private String companyId;
    private boolean stopAll;
    private String reason;
}