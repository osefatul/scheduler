package com.usbank.corp.dcr.api.model;

import lombok.Data;
import java.util.Date;

@Data
public class CampaignWaitStatusDTO {
    private String campaignId;
    private Date nextEligibleDate;
    private String closureReason;
    private Integer closureCount;
}