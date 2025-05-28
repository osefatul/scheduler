package com.usbank.corp.dcr.api.model;

import lombok.Data;

@Data
public class InsightClosureResponse {
    private String userId;
    private String campaignId;
    private String action; // HIDE_UNTIL_NEXT_ELIGIBLE, ASK_SEE_AGAIN, ASK_STOP_ALL
    private String message;
    private boolean showDialog;
    private String dialogType; // SEE_AGAIN, STOP_ALL
    private String nextEligibleDate;
    private int closureCount; // How many times user has closed this campaign
}