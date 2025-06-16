// Enhanced Banner.tsx - COMPLETE FIXED VERSION
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
    const [forceHide, setForceHide] = useState(false);
    
    // Session closure management
    const { isCampaignClosed, hasUserClosures, sessionClosures } = useSessionClosureManager();
    
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
          currentBannerHidden: bannerHidden,
          forceHide
        });
        
        if (isClosedInSession || isGloballyOptedOut) {
          console.log(`Banner ${campaign.campaignId} should be hidden`);
          setBannerHidden(true);
          setForceHide(true);
        }
      }
    }, [campaign?.campaignId, userId, companyId, isCampaignClosed, optOutResponse]);

    // Monitor session changes
    useEffect(() => {
      if (campaign?.campaignId && userId && companyId) {
        const isClosedInSession = isCampaignClosed(campaign.campaignId, userId, companyId);
        if (isClosedInSession && !forceHide) {
          console.log(`Session closure detected for ${campaign.campaignId} - force hiding banner`);
          setForceHide(true);
          setBannerHidden(true);
        }
      }
    }, [sessionClosures, campaign?.campaignId, userId, companyId, isCampaignClosed, forceHide]);

    // Handle banner closure callback - CRITICAL FIX
    const handleBannerClosed = useCallback((campaignId: string, closureCount: number) => {
      console.log(`✅ CRITICAL: Banner ${campaignId} closure complete - FORCE HIDING NOW`);
      
      // IMMEDIATE: Force hide at parent level
      setForceHide(true);
      setBannerHidden(true);
      
      // Double-check session state
      setTimeout(() => {
        if (userId && companyId) {
          const isClosedInSession = isCampaignClosed(campaignId, userId, companyId);
          console.log(`Post-closure session check: ${isClosedInSession}`);
          if (!isClosedInSession) {
            console.error('WARNING: Session state not updated!');
          }
        }
      }, 100);
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
      // CRITICAL: Check both bannerHidden AND forceHide
      if (bannerHidden || forceHide) {
        console.log('Banner hidden due to closure state or force hide');
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