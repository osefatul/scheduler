package com.usbank.corp.dcr.api.model;

import lombok.Data;

@Data
public class SeeAgainRequest {
    private String userId;
    private String companyId;
    private String campaignId;
    private boolean wantToSeeAgain;
    private String reason;
}