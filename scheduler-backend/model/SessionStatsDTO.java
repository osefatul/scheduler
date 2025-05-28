package com.usbank.corp.dcr.api.model;

import lombok.Data;

@Data
public class SessionStatsDTO {
    private String sessionId;
    private String userId;
    private String companyId;
    private boolean userEligible;
    private String preferenceSummary;
    private int activeCampaignsCount;
}