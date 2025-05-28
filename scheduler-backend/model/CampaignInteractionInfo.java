public static class CampaignInteractionInfo {
  private String userId;
  private String companyId;
  private String campaignId;
  private int closureCount;
  private String preferenceType;
  private boolean inCoolingPeriod;
  private Date nextEligibleDate;
  private String optOutReason;
  private String nextClosureAction;
  private boolean eligible;
  
  // Getters and setters
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  
  public String getCompanyId() { return companyId; }
  public void setCompanyId(String companyId) { this.companyId = companyId; }
  
  public String getCampaignId() { return campaignId; }
  public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
  
  public int getClosureCount() { return closureCount; }
  public void setClosureCount(int closureCount) { this.closureCount = closureCount; }
  
  public String getPreferenceType() { return preferenceType; }
  public void setPreferenceType(String preferenceType) { this.preferenceType = preferenceType; }
  
  public boolean isInCoolingPeriod() { return inCoolingPeriod; }
  public void setInCoolingPeriod(boolean inCoolingPeriod) { this.inCoolingPeriod = inCoolingPeriod; }
  
  public Date getNextEligibleDate() { return nextEligibleDate; }
  public void setNextEligibleDate(Date nextEligibleDate) { this.nextEligibleDate = nextEligibleDate; }
  
  public String getOptOutReason() { return optOutReason; }
  public void setOptOutReason(String optOutReason) { this.optOutReason = optOutReason; }
  
  public String getNextClosureAction() { return nextClosureAction; }
  public void setNextClosureAction(String nextClosureAction) { this.nextClosureAction = nextClosureAction; }
  
  public boolean isEligible() { return eligible; }
  public void setEligible(boolean eligible) { this.eligible = eligible; }
}