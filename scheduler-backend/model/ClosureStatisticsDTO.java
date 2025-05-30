package com.usbank.corp.dcr.api.model;

import lombok.Data;

@Data
public class ClosureStatisticsDTO {
    
    private String campaignId;
    private Long firstTimeClosures;
    private Long secondTimeClosures;
    private Long multipleClosures;
    private Long permanentClosures;
    private Double closureRate;
}