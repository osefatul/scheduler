// sessionClosureManager.ts
export interface ClosureSessionData {
  campaignId: string;
  userId: string;
  companyId: string;
  closureCount: number;
  closedAt: string;
  sessionId: string;
  action: string; // 'FIRST_CLOSURE', 'SECOND_CLOSURE', 'PERMANENT_BLOCK', etc.
}

class SessionClosureManager {
  private static readonly STORAGE_KEY = 'dcr_campaign_closures';
  private static readonly SESSION_KEY = 'dcr_session_id';
  
  constructor() {
    // Initialize session ID if not exists
    if (!this.getSessionId()) {
      this.initializeSession();
    }
  }

  private initializeSession(): void {
    const sessionId = `dcr_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    sessionStorage.setItem(SessionClosureManager.SESSION_KEY, sessionId);
  }

  private getSessionId(): string | null {
    return sessionStorage.getItem(SessionClosureManager.SESSION_KEY);
  }

  /**
   * Get all closures for current session
   */
  private getSessionClosures(): ClosureSessionData[] {
    try {
      const stored = sessionStorage.getItem(SessionClosureManager.STORAGE_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch (error) {
      console.error('Error parsing session closures:', error);
      return [];
    }
  }

  /**
   * Save closure data to session
   */
  private saveSessionClosures(closures: ClosureSessionData[]): void {
    try {
      sessionStorage.setItem(SessionClosureManager.STORAGE_KEY, JSON.stringify(closures));
    } catch (error) {
      console.error('Error saving session closures:', error);
    }
  }

  /**
   * Record a campaign closure in session
   */
  recordClosure(
    campaignId: string,
    userId: string,
    companyId: string,
    closureCount: number,
    action: string
  ): void {
    const sessionId = this.getSessionId();
    if (!sessionId) return;

    const closures = this.getSessionClosures();
    
    // Remove any existing closure for this campaign in this session
    const filteredClosures = closures.filter(
      c => !(c.campaignId === campaignId && c.userId === userId && c.companyId === companyId)
    );
    
    // Add new closure record
    const newClosure: ClosureSessionData = {
      campaignId,
      userId,
      companyId,
      closureCount,
      closedAt: new Date().toISOString(),
      sessionId,
      action
    };
    
    filteredClosures.push(newClosure);
    this.saveSessionClosures(filteredClosures);
    
    console.log(`Session closure recorded: ${campaignId} (count: ${closureCount}, action: ${action})`);
  }

  /**
   * Check if a campaign is closed in current session
   * CORRECTED: Only hide if user made a permanent choice, not if they chose "show later"
   */
  isCampaignClosedInSession(campaignId: string, userId: string, companyId: string): boolean {
    const closures = this.getSessionClosures();
    const sessionId = this.getSessionId();
    
    const closure = closures.find(
      c => c.campaignId === campaignId && 
           c.userId === userId && 
           c.companyId === companyId &&
           c.sessionId === sessionId
    );
    
    // CRITICAL: Only consider closed if user made permanent choices
    // FIRST_CLOSURE_HIDE = first time close (hide for session)
    // PERMANENT_BLOCK = user said "don't show again" 
    // GLOBAL_OPT_OUT = user opted out globally
    // NOT TEMPORARY_CLOSE = user chose "show later" (should show modal again)
    const permanentActions = ['FIRST_CLOSURE_HIDE', 'PERMANENT_BLOCK', 'GLOBAL_OPT_OUT'];
    const isClosed = closure && permanentActions.includes(closure.action);
    
    if (isClosed) {
      console.log(`Campaign ${campaignId} is closed in session (${closure.action})`);
    } else if (closure && closure.action === 'TEMPORARY_CLOSE') {
      console.log(`Campaign ${campaignId} chose "show later" - will show modal again (${closure.action})`);
    } else if (closure) {
      console.log(`Campaign ${campaignId} has closure record but can show modal (${closure.action})`);
    }
    
    return !!isClosed;
  }

  /**
   * Get closure data for a specific campaign in current session
   */
  getSessionClosure(campaignId: string, userId: string, companyId: string): ClosureSessionData | null {
    const closures = this.getSessionClosures();
    const sessionId = this.getSessionId();
    
    return closures.find(
      c => c.campaignId === campaignId && 
           c.userId === userId && 
           c.companyId === companyId &&
           c.sessionId === sessionId
    ) || null;
  }

  /**
   * Clear all session closures (for testing or session reset)
   */
  clearSessionClosures(): void {
    sessionStorage.removeItem(SessionClosureManager.STORAGE_KEY);
    console.log('Session closures cleared');
  }

  /**
   * Get all closures for current session (for debugging)
   */
  getAllSessionClosures(): ClosureSessionData[] {
    return this.getSessionClosures().filter(c => c.sessionId === this.getSessionId());
  }

  /**
   * Check if user has any closures in current session
   */
  hasUserClosuresInSession(userId: string, companyId: string): boolean {
    const closures = this.getSessionClosures();
    const sessionId = this.getSessionId();
    
    return closures.some(c => 
      c.userId === userId && 
      c.companyId === companyId && 
      c.sessionId === sessionId
    );
  }
}

// Export singleton instance
export const sessionClosureManager = new SessionClosureManager();

// React hook for using session closure manager
import { useCallback, useEffect, useState } from 'react';

export const useSessionClosureManager = () => {
  const [sessionClosures, setSessionClosures] = useState<ClosureSessionData[]>([]);

  const refreshClosures = useCallback(() => {
    setSessionClosures(sessionClosureManager.getAllSessionClosures());
  }, []);

  useEffect(() => {
    refreshClosures();
  }, [refreshClosures]);

  const recordClosure = useCallback((
    campaignId: string,
    userId: string, 
    companyId: string,
    closureCount: number,
    action: string
  ) => {
    sessionClosureManager.recordClosure(campaignId, userId, companyId, closureCount, action);
    refreshClosures();
  }, [refreshClosures]);

  const isCampaignClosed = useCallback((
    campaignId: string,
    userId: string,
    companyId: string
  ) => {
    return sessionClosureManager.isCampaignClosedInSession(campaignId, userId, companyId);
  }, []);

  return {
    sessionClosures,
    recordClosure,
    isCampaignClosed,
    refreshClosures,
    clearSessionClosures: sessionClosureManager.clearSessionClosures.bind(sessionClosureManager),
    hasUserClosures: (userId: string, companyId: string) => 
      sessionClosureManager.hasUserClosuresInSession(userId, companyId)
  };
};