// Enhanced Banner.tsx - COMPLETE UPDATED VERSION
import React, { useEffect, useState, useCallback } from "react";
import {
  ExternalPortalContainer,
  ExternalPortalBannerContainer,
  LoadingIndicator,
  ErrorDisplay,
} from "./Banner.styled";
import { USBBanner } from "../usb-banner";
import { ButtonProps } from "./IBanner";
import { useRotateCampaignNextQuery } from "@/external/services/campaignAPI";
import { useGetBannerByIdQuery } from "@/external/services/bannerAPI";
import { useLocation } from "react-router-dom";
import { useSessionClosureManager } from './sessionClosureManager';
import { useCampaignSessionManager } from './campaignSessionManager';
import { useCheckOptOutStatusQuery } from './campaignClosureAPI';

export const createBanner = (
  campaign?: { 
    insightSubType?: string; 
    campaignId?: string;
    id?: string;
    bannerId?: string;
  }
) => {
  return ({
    bannerID,
    onNavigate,
    userId,
    companyId,
  }: {
    bannerID?: string;
    onNavigate?: any;
    userId?: string;
    companyId?: string;
  }) => {
    const [bannerHidden, setBannerHidden] = useState(false);
    
    // Session closure management
    const { isCampaignClosed, hasUserClosures } = useSessionClosureManager();
    
    // Check if user has globally opted out
    const { data: optOutResponse } = useCheckOptOutStatusQuery(
      userId || "", 
      { skip: !userId }
    );

    const navigate = (path: string, state?: any) => {
      if (onNavigate) {
        onNavigate(path, state);
      } else {
        window.location.href = path;
      }
    };

    const {
      data: BannersResponse,
      isLoading: isContentsListLoading,
      error: contentError,
    } = useGetBannerByIdQuery(Number(bannerID));

    // Check if banner should be hidden due to closure state
    useEffect(() => {
      if (campaign?.campaignId && userId && companyId) {
        // Check if campaign is closed in current session
        const isClosedInSession = isCampaignClosed(campaign.campaignId, userId, companyId);
        
        // Check if user has globally opted out
        const isGloballyOptedOut = optOutResponse?.success && optOutResponse?.data === true;
        
        console.log(`Banner visibility check for ${campaign.campaignId}:`, {
          isClosedInSession,
          isGloballyOptedOut,
          currentBannerHidden: bannerHidden
        });
        
        if (isClosedInSession || isGloballyOptedOut) {
          console.log(`Banner ${campaign.campaignId} should be hidden - closed in session: ${isClosedInSession}, globally opted out: ${isGloballyOptedOut}`);
          setBannerHidden(true);
        } else {
          console.log(`Banner ${campaign.campaignId} should be visible`);
          setBannerHidden(false);
        }
      }
    }, [campaign?.campaignId, userId, companyId, isCampaignClosed, optOutResponse, bannerHidden]);

    // ADDITIONAL: Force re-check when session closures change
    const { sessionClosures } = useSessionClosureManager();
    useEffect(() => {
      if (campaign?.campaignId && userId && companyId) {
        const isClosedInSession = isCampaignClosed(campaign.campaignId, userId, companyId);
        if (isClosedInSession && !bannerHidden) {
          console.log(`Session closure detected for ${campaign.campaignId} - hiding banner`);
          setBannerHidden(true);
        }
      }
    }, [sessionClosures, campaign?.campaignId, userId, companyId, isCampaignClosed, bannerHidden]);

    // Handle banner closure callback - ONLY called when user COMPLETES preference flow
    const handleBannerClosed = useCallback((campaignId: string, closureCount: number) => {
      console.log(`Banner ${campaignId} COMPLETED closure flow with count ${closureCount} - hiding from parent`);
      setBannerHidden(true);
      
      // CRITICAL: Also check session state to force hide
      if (userId && companyId) {
        const isClosedInSession = isCampaignClosed(campaignId, userId, companyId);
        console.log(`Banner ${campaignId} session closed status after preference:`, isClosedInSession);
        if (isClosedInSession) {
          setBannerHidden(true);
        }
      }
      
      // Additional logic can be added here for analytics, etc.
    }, [isCampaignClosed, userId, companyId]);

    const primaryButtonProps: ButtonProps = {
      label: "Get Started",
      emphasis: "heavy",
      ctaStyle: "standard",
      onClick: () => console.log("Primary button clicked"),
    };

    const secondaryButtonProps: ButtonProps = {
      label: "Learn More",
      emphasis: "subtle",
      ctaStyle: "standard",
      onClick: () => {
        console.log("Secondary button clicked", campaign?.insightSubType, campaign?.campaignId);
        navigate("/learn-more", {
          state: {
            insightSubType: campaign?.insightSubType,
            campaignId: campaign?.campaignId,
          },
        });
      }
    };

    const renderBannerContent = () => {
      // Don't render if banner is hidden due to closure
      if (bannerHidden) {
        console.log('Banner hidden due to closure state');
        return null;
      }

      if (isContentsListLoading) {
        return (
          <LoadingIndicator>
            <p>Loading banner content...</p>
          </LoadingIndicator>
        );
      }

      if (contentError) {
        return (
          <LoadingIndicator>
            <p>Please try again later.</p>
          </LoadingIndicator>
        );
      }

      if (!BannersResponse && !isContentsListLoading && !contentError && bannerID) {
        return (
          <ErrorDisplay>
            <p>No banner content available.</p>
          </ErrorDisplay>
        );
      }

      return (
        <USBBanner
          id={String(BannersResponse?.id)}
          title={BannersResponse?.title || ""}
          message={BannersResponse?.message || ""}
          bannerImageUrl={BannersResponse?.bannerImageUrl}
          bannerBackgroundColor={BannersResponse?.bannerBackgroundColor}
          width="100%"
          height="300px"
          maxWidth="1248px"
          containerWidth="1248"
          primaryButtonProps={primaryButtonProps}
          secondaryButtonProps={secondaryButtonProps}
          showButton={true}
          // Enhanced props for closure functionality
          campaignId={campaign?.campaignId || campaign?.id}
          userId={userId}
          companyId={companyId}
          onBannerClosed={handleBannerClosed}
        />
      );
    };

    return (
      <ExternalPortalContainer>
        <ExternalPortalBannerContainer>
          {renderBannerContent()}
        </ExternalPortalContainer>
      </ExternalPortalContainer>
    );
  };
};

interface BannerProps {
  onNavigate?: (pathname: string, state?: any) => void;
  userId?: string | null;
}

const Banner: React.FC<BannerProps> = ({ onNavigate, userId }) => {
  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const userIdParam = queryParams.get("userId") || userId;
  const companyIdParam = queryParams.get("companyId");
  const dateParam = queryParams.get("date");

  // Session management
  const isDCRSessionActive = sessionStorage.getItem("isDCRSessionActive") === "true";
  
  // NEW: Campaign session management - Store and retrieve campaign data
  const { storedCampaign, storeCampaignData, hasStoredCampaignData } = useCampaignSessionManager();
  
  // NEW: Smart API skipping - skip if session is active AND we have stored campaign
  const shouldSkipAPI = isDCRSessionActive && hasStoredCampaignData();
  
  console.log('Banner render state:', {
    isDCRSessionActive,
    hasStoredCampaignData: hasStoredCampaignData(),
    shouldSkipAPI,
    storedCampaign
  });
  
  const { data: nextData, error: rotationError } = useRotateCampaignNextQuery(
    shouldSkipAPI ? null : { 
      username: userIdParam || "CORY", 
      company: companyIdParam || "ABCCORP", 
      date: dateParam || "20250715" 
    },
    { skip: shouldSkipAPI }
  );

  // Session closure management
  const { hasUserClosures, isCampaignClosed, sessionClosures } = useSessionClosureManager();

  // NEW: Set session active and store campaign data when API call succeeds
  useEffect(() => {
    if (!isDCRSessionActive && nextData?.success && nextData.data) {
      console.log("API called successfully. Setting session to active and storing campaign data.");
      sessionStorage.setItem("isDCRSessionActive", "true");
      storeCampaignData(nextData.data);
    } else if (isDCRSessionActive && hasStoredCampaignData()) {
      console.log("DCR session is already active and has stored campaign data. API call skipped.");
    } else if (isDCRSessionActive) {
      console.log("DCR session is active but no stored campaign data found.");
    }
  }, [isDCRSessionActive, nextData, storeCampaignData, hasStoredCampaignData]);

  // Debug: Log closure state
  useEffect(() => {
    if (userIdParam && companyIdParam) {
      const hasClosures = hasUserClosures(userIdParam, companyIdParam);
      console.log(`User ${userIdParam} has closures in session:`, hasClosures);
    }
  }, [userIdParam, companyIdParam, hasUserClosures]);

  // Handle rotation errors
  if (rotationError) {
    console.error('Campaign rotation error:', rotationError);
    return null;
  }

  // NEW: Determine which campaign data to use - fresh API or stored session data
  let campaignToShow = null;
  
  if (nextData?.success === true && nextData.data) {
    // Fresh API response - use this data
    campaignToShow = nextData.data;
    console.log('Using fresh campaign data from API:', campaignToShow);
  } else if (isDCRSessionActive && storedCampaign) {
    // Use stored campaign data for same session
    campaignToShow = {
      campaignId: storedCampaign.campaignId,
      id: storedCampaign.campaignId,
      bannerId: storedCampaign.bannerId,
      insightSubType: storedCampaign.insightSubType,
      insightType: storedCampaign.insightType,
      name: storedCampaign.name
    };
    console.log('Using stored campaign data from session:', campaignToShow);
  }

  // Check if campaign should be hidden due to session closures
  const { isCampaignClosed } = useSessionClosureManager();
  
  if (campaignToShow && userIdParam && companyIdParam) {
    const isClosedInSession = isCampaignClosed(campaignToShow.campaignId || campaignToShow.id, userIdParam, companyIdParam);
    
    if (isClosedInSession) {
      console.log(`Campaign ${campaignToShow.campaignId || campaignToShow.id} is closed in this session, not showing banner`);
      return null;
    }
  }

  // Render banner if we have campaign data
  if (campaignToShow) {
    const BannerComponent = createBanner(campaignToShow);

    return (
      <BannerComponent 
        bannerID={campaignToShow.bannerId} 
        onNavigate={onNavigate}
        userId={userIdParam}
        companyId={companyIdParam}
      />
    );
  }

  // If rotation API returned success: false, don't show banner
  if (nextData?.success === false) {
    console.log('Campaign rotation returned no eligible campaigns:', nextData.message);
    return null;
  }

  // Loading state or no data yet
  if (!isDCRSessionActive) {
    console.log('Waiting for campaign rotation API response...');
  } else if (!hasStoredCampaignData()) {
    console.log('Session is active but no stored campaign data available');
  }
  
  return null;
};

export default Banner;