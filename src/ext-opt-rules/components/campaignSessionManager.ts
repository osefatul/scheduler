// campaignSessionManager.ts
export interface CampaignSessionData {
  campaignId: string;
  bannerId: string;
  insightSubType?: string;
  insightType?: string;
  name?: string;
  // Add other campaign fields you need
  fetchedAt: string;
  sessionId: string;
}

class CampaignSessionManager {
  private static readonly CAMPAIGN_STORAGE_KEY = 'dcr_current_campaign';
  private static readonly SESSION_KEY = 'dcr_session_id';
  
  constructor() {
    // Initialize session ID if not exists
    if (!this.getSessionId()) {
      this.initializeSession();
    }
  }

  private initializeSession(): void {
    const sessionId = `dcr_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    sessionStorage.setItem(CampaignSessionManager.SESSION_KEY, sessionId);
  }

  private getSessionId(): string | null {
    return sessionStorage.getItem(CampaignSessionManager.SESSION_KEY);
  }

  /**
   * Store campaign data from successful /next API call
   */
  storeCampaignData(campaignData: any): void {
    const sessionId = this.getSessionId();
    if (!sessionId || !campaignData) return;

    const sessionData: CampaignSessionData = {
      campaignId: campaignData.id || campaignData.campaignId,
      bannerId: campaignData.bannerId,
      insightSubType: campaignData.insightSubType,
      insightType: campaignData.insightType,
      name: campaignData.name,
      fetchedAt: new Date().toISOString(),
      sessionId
    };

    try {
      sessionStorage.setItem(CampaignSessionManager.CAMPAIGN_STORAGE_KEY, JSON.stringify(sessionData));
      console.log('Campaign data stored for session:', sessionData);
    } catch (error) {
      console.error('Error storing campaign data:', error);
    }
  }

  /**
   * Get stored campaign data for current session
   */
  getStoredCampaignData(): CampaignSessionData | null {
    const sessionId = this.getSessionId();
    if (!sessionId) return null;

    try {
      const stored = sessionStorage.getItem(CampaignSessionManager.CAMPAIGN_STORAGE_KEY);
      if (!stored) return null;

      const campaignData: CampaignSessionData = JSON.parse(stored);
      
      // Verify it belongs to current session
      if (campaignData.sessionId !== sessionId) {
        console.log('Campaign data is from different session, clearing');
        this.clearCampaignData();
        return null;
      }

      return campaignData;
    } catch (error) {
      console.error('Error parsing stored campaign data:', error);
      return null;
    }
  }

  /**
   * Check if we have valid campaign data for current session
   */
  hasStoredCampaignData(): boolean {
    return this.getStoredCampaignData() !== null;
  }

  /**
   * Clear stored campaign data
   */
  clearCampaignData(): void {
    sessionStorage.removeItem(CampaignSessionManager.CAMPAIGN_STORAGE_KEY);
    console.log('Campaign data cleared');
  }

  /**
   * Clear all session data (for testing or session reset)
   */
  clearAllSessionData(): void {
    sessionStorage.removeItem(CampaignSessionManager.CAMPAIGN_STORAGE_KEY);
    sessionStorage.removeItem(CampaignSessionManager.SESSION_KEY);
    console.log('All session data cleared');
  }
}

// Export singleton instance
export const campaignSessionManager = new CampaignSessionManager();

// React hook for using campaign session manager
import { useCallback, useEffect, useState } from 'react';

export const useCampaignSessionManager = () => {
  const [storedCampaign, setStoredCampaign] = useState<CampaignSessionData | null>(null);

  const refreshStoredCampaign = useCallback(() => {
    setStoredCampaign(campaignSessionManager.getStoredCampaignData());
  }, []);

  useEffect(() => {
    refreshStoredCampaign();
  }, [refreshStoredCampaign]);

  const storeCampaignData = useCallback((campaignData: any) => {
    campaignSessionManager.storeCampaignData(campaignData);
    refreshStoredCampaign();
  }, [refreshStoredCampaign]);

  return {
    storedCampaign,
    storeCampaignData,
    hasStoredCampaignData: () => campaignSessionManager.hasStoredCampaignData(),
    clearCampaignData: () => {
      campaignSessionManager.clearCampaignData();
      refreshStoredCampaign();
    },
    refreshStoredCampaign
  };
};