// WarmLeadTrackingDTO.java
package com.ubank.corp.dcr.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarmLeadTrackingDTO {
    
    private String id;
    private String userId;
    private String campaignId;
    private String insightSubType;
    private Integer visitCount;
    private LocalDateTime firstVisitDate;
    private LocalDateTime lastVisitDate;
    private String userAgent;
    private String referrerUrl;
    private Boolean isConvertedToHot;
    private LocalDateTime convertedToHotDate;
    private String message; // For response messages
}