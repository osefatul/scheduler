// New DTO: CampaignInteractionRequest.java
package com.usbank.corp.dcr.api.model;

import lombok.Data;

@Data
public class CampaignInteractionRequest {
    private String userId;
    private String companyId;
    private String campaignId;
}