
import React, { useEffect, useState, useCallback } from "react";
import {
  ExternalPortalContainer,
  ExternalPortalBannerContainer,
  LoadingIndicator,
  ErrorDisplay,
} from "./Banner.styled";
import { useNavigate } from "react-router-dom";
import { USBBanner } from "../usb-banner";
import { ButtonProps } from "./IBanner";
import { useRotateCampaignNextQuery } from "@/external/services/campaignAPI";
import { useGetBannerByIdQuery } from "@/external/services/bannerAPI";
import { useLocation } from "react-router-dom";
import { useCheckOptOutStatusQuery } from "@/external/services/campaignClosureAPI";
import { useCampaignSessionManager } from "@/external/utils/campaignSessionManager";
import { useSessionClosureManager } from "@/external/utils/sessionClosureManager";
import { GlobalSuccessPopup } from "../bannerpopup/FirstClosurePopup";

export const createBanner = (campaign?: {
  insightSubType?: string;
  campaignId?: string;
  id?: string;
  bannerId?: string;
}) => {
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
    const { isCampaignClosed } = useSessionClosureManager();
    const { data: optOutResponse } = useCheckOptOutStatusQuery(userId || "", {
      skip: !userId,
    });
    const routerNavigate = useNavigate();

    const navigate = (path: string, state?: any) => {
      if (onNavigate) {
        onNavigate(path, state);
      } else {
        routerNavigate(path, { state });
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
        const isClosedInSession = isCampaignClosed(
          campaign.campaignId,
          userId,
          companyId
        );

        // Check if user has globally opted out
        const isGloballyOptedOut =
          optOutResponse?.success && optOutResponse?.data === true;

        if (isClosedInSession || isGloballyOptedOut) {
          setBannerHidden(true);
        } else {
          setBannerHidden(false);
        }
      }
    }, [
      campaign?.campaignId,
      userId,
      companyId,
      isCampaignClosed,
      optOutResponse,
      bannerHidden,
    ]);

    const { sessionClosures } = useSessionClosureManager();
    useEffect(() => {
      if (campaign?.campaignId && userId && companyId) {
        const isClosedInSession = isCampaignClosed(
          campaign.campaignId,
          userId,
          companyId
        );
        if (isClosedInSession && !bannerHidden) {
          setBannerHidden(true);
        }
      }
    }, [
      sessionClosures,
      campaign?.campaignId,
      userId,
      companyId,
      isCampaignClosed,
      bannerHidden,
    ]);

    const handleBannerClosed = useCallback(
      (campaignId: string) => {
        setBannerHidden(true);

        if (userId && companyId) {
          const isClosedInSession = isCampaignClosed(
            campaignId,
            userId,
            companyId
          );
          if (isClosedInSession) {
            setBannerHidden(true);
          }
        }
      },
      [isCampaignClosed, userId, companyId]
    );

    const primaryButtonProps: ButtonProps = {
      label: "Get Started",
      emphasis: "heavy",
      ctaStyle: "standard",
      onClick: () => {},
    };

    const secondaryButtonProps: ButtonProps = {
      label: "Learn More",
      emphasis: "subtle",
      ctaStyle: "standard",
      onClick: () => {
        if (!campaign?.insightSubType) {
          console.error("No product name available!");
          return;
        }

        const productName = encodeURIComponent(campaign.insightSubType);
        const campaignId = encodeURIComponent(campaign?.id ?? "");
        const user = encodeURIComponent(userId ?? "");
        const company = encodeURIComponent(companyId ?? "");
        navigate(
          `/learn-more?productname=${productName}&campaignId=${campaignId}&userId=${user}&companyId=${company}`,
        );
      },
    };

    const renderBannerContent = () => {
      if (bannerHidden) {
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

      if (
        !BannersResponse &&
        !isContentsListLoading &&
        !contentError &&
        bannerID
      ) {
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
          campaignId={campaign?.campaignId || campaign?.id}
          userId={userId}
          companyId={companyId}
          onBannerClosed={handleBannerClosed}
        />
      );
    };

    return (
      <>
        <ExternalPortalContainer>
          <ExternalPortalBannerContainer>
            {renderBannerContent()}
          </ExternalPortalBannerContainer>
        </ExternalPortalContainer>

        <GlobalSuccessPopup />
      </>
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
  let dateParam = queryParams.get("date");

  // If no dateParam, use current date in YYYYMMDD format
  if (!dateParam) {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    dateParam = `${yyyy}${mm}${dd}`;
  }

  const isDCRSessionActive =
    sessionStorage.getItem("isDCRSessionActive") === "true";

  const { storedCampaign, storeCampaignData, hasStoredCampaignData } =
    useCampaignSessionManager();
  const shouldSkipAPI = isDCRSessionActive && hasStoredCampaignData();

  const { data: nextData, error: rotationError } = useRotateCampaignNextQuery(
    shouldSkipAPI
      ? null
      : {
          username: userIdParam || "CORY",
          company: companyIdParam || "ABCCORP",
          date: dateParam,
        },
    { skip: shouldSkipAPI }
  );

  const { isCampaignClosed } = useSessionClosureManager();

  useEffect(() => {
    if (!isDCRSessionActive && nextData?.success && nextData.data) {
      sessionStorage.setItem("isDCRSessionActive", "true");
      storeCampaignData(nextData.data);
    } else if (isDCRSessionActive && hasStoredCampaignData()) {
    } else if (isDCRSessionActive) {
    }
  }, [isDCRSessionActive, nextData, storeCampaignData, hasStoredCampaignData]);

  if (rotationError) {
    return <GlobalSuccessPopup />;
  }

  let campaignToShow = null;

  if (nextData?.success === true && nextData.data) {
    campaignToShow = nextData.data;
  } else if (isDCRSessionActive && storedCampaign) {
    campaignToShow = {
      campaignId: storedCampaign.campaignId,
      id: storedCampaign.campaignId,
      bannerId: storedCampaign.bannerId,
      insightSubType: storedCampaign.insightSubType,
      insightType: storedCampaign.insightType,
      name: storedCampaign.name,
    };
  }

  if (campaignToShow && userIdParam && companyIdParam) {
    const isClosedInSession = isCampaignClosed(
      campaignToShow.campaignId || campaignToShow.id,
      userIdParam,
      companyIdParam
    );

    if (isClosedInSession) {
      return <GlobalSuccessPopup />;
    }
  }

  if (campaignToShow) {
    const BannerComponent = createBanner(campaignToShow);

    return (
      <BannerComponent
        bannerID={campaignToShow.bannerId}
        onNavigate={onNavigate}
        userId={userIdParam || undefined}
        companyId={companyIdParam || undefined}
      />
    );
  }

  if (nextData?.success === false) {
    return <GlobalSuccessPopup />;
  }

  if (!isDCRSessionActive) {
  } else if (!hasStoredCampaignData()) {
  }

  return <GlobalSuccessPopup />;
};

export default Banner;

