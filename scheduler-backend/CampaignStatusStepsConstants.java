public @Getter
@AllArgsConstructor
public enum CampaignStatusStepsConstants {
    
    // ACTION (STATUS, CAMPAIGN_STEPS) in CampaignTable
    SETUP("DRAFT", "MAP_INSIGHT"),
    CONFIGURE("DRAFT", "CAMPAIGN_DURATION"),
    REVIEW("DRAFT", "REVIEW"),
    SUBMIT("INPROGRESS", "SUBMIT");
    
    String status;
    String campaignSteps;
    
    /**
     * Get status based on action
     * 
     * @param action Action value
     * @return Status
     */
    public static String getStatusForAction(String action) {
        for (CampaignStatusStepsConstants constant : values()) {
            if (constant.name().equalsIgnoreCase(action)) {
                return constant.getStatus();
            }
        }
        return "DRAFT"; // Default
    }
    
    /**
     * Get campaign steps based on action
     * 
     * @param action Action value
     * @return Campaign steps
     */
    public static String getCampaignStepsForAction(String action) {
        for (CampaignStatusStepsConstants constant : values()) {
            if (constant.name().equalsIgnoreCase(action)) {
                return constant.getCampaignSteps();
            }
        }
        return "MAP_INSIGHT"; // Default
    }
} {
  
}
