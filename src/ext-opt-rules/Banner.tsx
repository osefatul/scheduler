// Enhanced Banner.tsx
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
        
        if (isClosedInSession || isGloballyOptedOut) {
          console.log(`Banner ${campaign.campaignId} hidden - closed in session: ${isClosedInSession}, globally opted out: ${isGloballyOptedOut}`);
          setBannerHidden(true);
        } else {
          setBannerHidden(false);
        }
      }
    }, [campaign?.campaignId, userId, companyId, isCampaignClosed, optOutResponse]);

    // Handle banner closure callback
    const handleBannerClosed = useCallback((campaignId: string, closureCount: number) => {
      console.log(`Banner ${campaignId} closed with count ${closureCount}`);
      setBannerHidden(true);
      
      // Additional logic can be added here for analytics, etc.
    }, []);

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
        </ExternalPortalBannerContainer>
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
  
  // Campaign rotation query
  const { data: nextData, error: rotationError } = useRotateCampaignNextQuery(
    isDCRSessionActive ? null : { 
      username: userIdParam || "CORY", 
      company: companyIdParam || "ABCCORP", 
      date: dateParam || "20250715" 
    },
    { skip: isDCRSessionActive }
  );

  // Session closure management
  const { hasUserClosures, clearSessionClosures } = useSessionClosureManager();

  // Set session active when API call succeeds
  useEffect(() => {
    if (!isDCRSessionActive && nextData?.success) {
      console.log("API called successfully. Setting session to active.");
      sessionStorage.setItem("isDCRSessionActive", "true");
    } else if (isDCRSessionActive) {
      console.log("DCR session is already active. API call skipped.");
    }
  }, [isDCRSessionActive, nextData]);

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

  // Render banner if we have campaign data and user hasn't globally opted out
  if (nextData?.success === true) {
    const campaign = nextData.data;
    
    if (!campaign) {
      console.log('No campaign data available');
      return null;
    }

    const BannerComponent = createBanner(campaign);

    return (
      <BannerComponent 
        bannerID={campaign?.bannerId} 
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
  return null;
};

export default Banner;