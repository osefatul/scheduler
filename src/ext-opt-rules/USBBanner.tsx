// Enhanced USBBanner.tsx - COMPLETE NEW APPROACH
import React, { useState, useEffect, useCallback, useRef } from "react";
import { BannerProps } from "./IUSBBanner";
import {
  getBannerImageWidth,
  BannerContainer,
  BannerContent,
  BannerTitle,
  BannerMessage,
  ButtonContainer,
  StyledUSBButtonSecondary,
  BannerImageContainer,
  BannerImage,
  ActionContainer,
  BannercloseIcon,
} from "./USBBanner.styled";

import { USBIconClose } from "@usb-shield/react-icons";
import { useCloseInsightMutation } from "./campaignClosureAPI";
import { useSessionClosureManager } from "./sessionClosureManager";

import SecondInsightBannerpopup from "../bannerpopup/SecondClosurePopup";
import FirstClosurePopup from "../bannerpopup/FirstClosurePopup";

interface EnhancedBannerProps extends BannerProps {
  campaignId?: string;
  userId?: string;
  companyId?: string;
  onBannerClosed?: (campaignId: string, closureCount: number) => void;
}

export const createUSBBanner = (): React.FC<EnhancedBannerProps> => {
  return ({
    id,
    title,
    message,
    bannerImageUrl,
    bannerBackgroundColor,
    showRemoveButton = false,
    showButton = false,
    onRemove,
    width,
    maxWidth,
    height,
    primaryButtonProps = {
      label: "Contact us",
      emphasis: "heavy",
      ctaStyle: "standard",
    },
    secondaryButtonProps = {
      label: "Learn more",
      emphasis: "subtle",
      ctaStyle: "standard",
    },
    removeButton,
    marginBottom,
    containerWidth,
    campaignId,
    userId,
    companyId,
    onBannerClosed,
    ...restProps
  }) => {
    const [isFirstModalOpen, setIsFirstModalOpen] = useState(false);
    const [isSecondModalOpen, setIsSecondModalOpen] = useState(false);
    const [closureCount, setClosureCount] = useState(0);
    const [isWaitingForUserAction, setIsWaitingForUserAction] = useState(false);
    const [shouldHideBanner, setShouldHideBanner] = useState(false);

    // Use ref to track if we've already processed the closure
    const hasProcessedClosure = useRef(false);

    // API and session management hooks
    const [closeInsight, { isLoading: isClosingInsight }] = useCloseInsightMutation();
    const { isCampaignClosed, recordClosure, sessionClosures } = useSessionClosureManager();

    // Check if banner should be hidden on mount
    useEffect(() => {
      if (campaignId && userId && companyId) {
        const isClosedInSession = isCampaignClosed(campaignId, userId, companyId);
        if (isClosedInSession) {
          console.log(`USBBanner: Campaign ${campaignId} is already closed in session`);
          setShouldHideBanner(true);
        }
      }
    }, [campaignId, userId, companyId, isCampaignClosed]);

    // Extract color information
    const backgroundColor = bannerBackgroundColor?.split(":")?.[1]?.trim() || "#ffffff";
    const colorName = bannerBackgroundColor?.split(":")?.[0]?.trim() || "Surface";
    const textColor = colorName === "Muted" ? "#ffffff" : "#4b5563";
    const isMuted = colorName === "Muted";

    // Calculate image width
    const imageWidth = getBannerImageWidth(
      title,
      message,
      containerWidth ? parseInt(containerWidth) : undefined
    );

    const handleRemove = (): void => {
      if (onRemove && typeof onRemove === "function") {
        onRemove(title || id || "");
      }
    };

    // Handle closure API call
    const handleClosureAPI = useCallback(async () => {
      if (!campaignId || !userId || !companyId) {
        console.error("Missing required params for closure API");
        return null;
      }

      try {
        console.log(`Calling closure API for campaign ${campaignId}`);
        const response = await closeInsight({
          userId,
          companyId,
          campaignId,
          closureDate: new Date().toISOString().split("T")[0],
        }).unwrap();

        console.log("Closure API response:", response);

        if (response.success && response.data) {
          const closureData = response.data;
          setClosureCount(closureData.closureCount);

          // Always record closure for session tracking
          recordClosure(
            campaignId,
            userId,
            companyId,
            closureData.closureCount,
            closureData.action || "CLOSURE_RECORDED"
          );

          return closureData;
        }
      } catch (error) {
        console.error("Error calling closure API:", error);
        return null;
      }
    }, [campaignId, userId, companyId, closeInsight, recordClosure]);

    // Handle close icon click
    const handleCloseIconClick = useCallback(async () => {
      if (isClosingInsight || hasProcessedClosure.current) return;

      console.log("Close icon clicked, calling closure API...");
      hasProcessedClosure.current = true;

      // Call closure API first
      const closureResponse = await handleClosureAPI();

      if (!closureResponse) {
        console.error("Closure API call failed");
        hasProcessedClosure.current = false;
        return;
      }

      console.log("Closure response:", closureResponse);

      // Handle based on closure count and action
      if (closureResponse.closureCount === 1 && closureResponse.action === "RECORDED_FIRST_CLOSURE") {
        console.log("FIRST CLOSURE - hiding banner immediately");
        setShouldHideBanner(true);
        
        if (onBannerClosed && campaignId) {
          onBannerClosed(campaignId, 1);
        }
      } else if (closureResponse.closureCount >= 2 || closureResponse.requiresUserInput === true) {
        console.log("SUBSEQUENT CLOSURE - showing modal");
        setIsWaitingForUserAction(true);
        
        if (closureResponse.isGlobalPrompt === true) {
          setIsSecondModalOpen(true);
        } else {
          setIsFirstModalOpen(true);
        }
      }
    }, [isClosingInsight, handleClosureAPI, campaignId, onBannerClosed]);

    // Handle preference completion
    const handlePreferenceComplete = useCallback(() => {
      console.log("✅ User completed entire preference flow - hiding banner");
      
      // Hide banner
      setShouldHideBanner(true);
      setIsWaitingForUserAction(false);
      hasProcessedClosure.current = false;
      
      // Notify parent
      if (onBannerClosed && campaignId) {
        onBannerClosed(campaignId, closureCount);
      }
    }, [onBannerClosed, campaignId, closureCount]);

    // Handle modal closures
    const handleFirstModalClose = useCallback(() => {
      console.log("First modal closing");
      setIsFirstModalOpen(false);
      // Reset flag if user just closes modal without completing preference
      if (!shouldHideBanner) {
        hasProcessedClosure.current = false;
        setIsWaitingForUserAction(false);
      }
    }, [shouldHideBanner]);

    const handleSecondModalClose = useCallback(() => {
      console.log("Second modal closing");
      setIsSecondModalOpen(false);
      // Reset flag if user just closes modal without completing preference
      if (!shouldHideBanner) {
        hasProcessedClosure.current = false;
        setIsWaitingForUserAction(false);
      }
    }, [shouldHideBanner]);

    // Don't render if banner should be hidden
    if (shouldHideBanner) {
      console.log('USBBanner is hidden');
      return null;
    }

    return (
      <>
        <BannerContainer
          width={width}
          maxWidth={maxWidth}
          height={height}
          backgroundColor={backgroundColor}
          marginBottom={marginBottom}
          {...restProps}
        >
          <BannercloseIcon
            onClick={handleCloseIconClick}
            style={{
              cursor: isClosingInsight || isWaitingForUserAction ? "not-allowed" : "pointer",
              opacity: isClosingInsight || isWaitingForUserAction ? 0.5 : 1,
            }}
          >
            <USBIconClose colorVariant={isMuted ? "light" : "default"} />
          </BannercloseIcon>

          <BannerContent>
            <BannerTitle textColor={textColor}>{title}</BannerTitle>
            <BannerMessage textColor={textColor}>{message}</BannerMessage>
            <ButtonContainer>
              {showButton && secondaryButtonProps && (
                <StyledUSBButtonSecondary
                  ctaStyle={secondaryButtonProps.ctaStyle}
                  emphasis={secondaryButtonProps.emphasis}
                  handleClick={secondaryButtonProps.onClick}
                  isMuted={isMuted}
                >
                  {secondaryButtonProps.label}
                </StyledUSBButtonSecondary>
              )}
            </ButtonContainer>
          </BannerContent>

          {bannerImageUrl && (
            <BannerImageContainer>
              <BannerImage
                src={bannerImageUrl}
                alt={title || "Banner"}
                maxWidth={imageWidth}
              />
            </BannerImageContainer>
          )}

          {showRemoveButton && (
            <ActionContainer onClick={handleRemove}>
              {removeButton || (
                <svg
                  className="remove-icon"
                  width="20"
                  height="20"
                  viewBox="0 0 24 24"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                    d="M19 6.41L17.59 5L12 10.59L6.41 5L5 6.41L10.59 12L5 17.59L6.41 19L12 13.41L17.59 19L19 17.59L13.41 12L19 6.41Z"
                    fill="currentColor"
                  />
                </svg>
              )}
            </ActionContainer>
          )}
        </BannerContainer>

        {/* Modals */}
        <FirstClosurePopup
          isOpen={isFirstModalOpen}
          handleClose={handleFirstModalClose}
          campaignId={campaignId}
          userId={userId}
          companyId={companyId}
          closureCount={closureCount}
          onPreferenceComplete={handlePreferenceComplete}
        />

        <SecondInsightBannerpopup
          isOpen={isSecondModalOpen}
          handleClose={handleSecondModalClose}
          campaignId={campaignId}
          userId={userId}
          companyId={companyId}
          closureCount={closureCount}
          onPreferenceComplete={handlePreferenceComplete}
        />
      </>
    );
  };
};

const USBBanner: React.FC<EnhancedBannerProps> = (props) => {
  const BannerComponent = createUSBBanner();
  return <BannerComponent {...props} />;
};

export default USBBanner;