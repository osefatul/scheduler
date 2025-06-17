import React, { useState, useEffect, useCallback } from "react";
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

import SecondInsightBannerpopup from "../bannerpopup/SecondClosurePopup";
import FirstClosurePopup from "../bannerpopup/FirstClosurePopup";
import { useCloseInsightMutation } from "@/external/services/campaignClosureAPI";
import { useSessionClosureManager } from "@/external/utils/sessionClosureManager";
import { useLocation } from "react-router-dom";

interface EnhancedBannerProps extends BannerProps {
  campaignId?: string;
  userId?: string;
  companyId?: string;
  onBannerClosed?: (campaignId: string) => void;
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
    const [isHidden, setIsHidden] = useState(false);
    const [closureCount, setClosureCount] = useState(0);

    const location = useLocation();
    const queryParams = new URLSearchParams(location.search);
    const dateParam = queryParams.get("date");

    const [closeInsight, { isLoading: isClosingInsight }] =
      useCloseInsightMutation();
    const { isCampaignClosed, recordClosure } = useSessionClosureManager();

    useEffect(() => {
      if (campaignId && userId && companyId) {
        const isClosedInSession = isCampaignClosed(
          campaignId,
          userId,
          companyId
        );
        if (isClosedInSession) {
          setIsHidden(true);
        }
      }
    }, [campaignId, userId, companyId, isCampaignClosed]);

    // Extract color information
    const backgroundColor =
      bannerBackgroundColor?.split(":")?.[1]?.trim() || "#ffffff";
    const colorName =
      bannerBackgroundColor?.split(":")?.[0]?.trim() || "Surface";
    const textColor = colorName === "Muted" ? "#ffffff" : "#4b5563";
    const isMuted = colorName === "Muted";

    // Calculate image width based on content length and container width
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

    const handleClosureAPI = useCallback(async () => {
      if (!campaignId || !userId || !companyId) {
        return null;
      }

      let effectiveDate: Date;
      if (dateParam && /^\d{8}$/.test(dateParam)) {
        const formatted = `${dateParam.slice(0, 4)}-${dateParam.slice(
          4,
          6
        )}-${dateParam.slice(6, 8)}`;
        effectiveDate = new Date(formatted);
      } else if (dateParam && !isNaN(Date.parse(dateParam))) {
        effectiveDate = new Date(dateParam);
      } else {
        effectiveDate = new Date();
      }

      try {
        const response = await closeInsight({
          userId,
          companyId,
          campaignId,
          closureDate: effectiveDate.toISOString().split("T")[0],
        }).unwrap();

        if (response.success && response.data) {
          const closureData = response.data;
          setClosureCount(closureData.closureCount);

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
    }, [campaignId, userId, companyId, closeInsight, recordClosure, dateParam]);

    const handleCloseIconClick = useCallback(async () => {
      if (isClosingInsight) return;

      const closureResponse = await handleClosureAPI();

      if (!closureResponse) {
        console.error("Closure API call failed");
        return;
      }

      if (
        closureResponse.closureCount === 1 &&
        closureResponse.action === "RECORDED_FIRST_CLOSURE"
      ) {
        setIsHidden(true);

        if (campaignId && userId && companyId)
          recordClosure(campaignId, userId, companyId, 1, "FIRST_CLOSURE_HIDE");

        if (onBannerClosed && campaignId) {
          onBannerClosed(campaignId);
        }
      } else if (
        closureResponse.closureCount >= 2 ||
        closureResponse.requiresUserInput === true
      ) {
        if (closureResponse.isGlobalPrompt === true) {
          setIsSecondModalOpen(true);
        } else {
          setIsFirstModalOpen(true);
        }

        // Keep banner visible while modal is open
        setIsHidden(false);
      } else {
        setIsFirstModalOpen(true);
        setIsHidden(false);
      }
    }, [
      isClosingInsight,
      handleClosureAPI,
      campaignId,
      userId,
      companyId,
      recordClosure,
      onBannerClosed,
    ]);

    // This gets called when user completes their preference choice
    // Success popup is now handled globally, not here
    const handleBannerClosureComplete = useCallback(() => {
      console.log('Banner closure complete - hiding banner');
      
      // Hide banner immediately
      setIsHidden(true);
      
      // Close any open modals
      setIsFirstModalOpen(false);
      setIsSecondModalOpen(false);

      // Notify parent component
      if (onBannerClosed && campaignId) {
        onBannerClosed(campaignId);
      }
    }, [onBannerClosed, campaignId]);

    const handleFirstModalClose = useCallback(() => {
      setIsFirstModalOpen(false);
    }, []);

    const handleSecondModalClose = useCallback(() => {
      setIsSecondModalOpen(false);
    }, []);

    // Don't render anything if banner is hidden and no modals are open
    if (isHidden && !isFirstModalOpen && !isSecondModalOpen) {
      return null;
    }

    // Show banner unless explicitly hidden (even when modals are open)
    const shouldShowBanner = !isHidden;

    return (
      <>
        {shouldShowBanner && (
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
                cursor: isClosingInsight ? "not-allowed" : "pointer",
                opacity: isClosingInsight ? 0.5 : 1,
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
        )}

        {/* Modals - Success popup is now handled globally */}
        {isFirstModalOpen && (
          <FirstClosurePopup
            isOpen={isFirstModalOpen}
            handleClose={handleFirstModalClose}
            campaignId={campaignId}
            userId={userId}
            companyId={companyId}
            closureCount={closureCount}
            onPreferenceComplete={handleBannerClosureComplete}
          />
        )}

        {isSecondModalOpen && (
          <SecondInsightBannerpopup
            isOpen={isSecondModalOpen}
            handleClose={handleSecondModalClose}
            campaignId={campaignId}
            userId={userId}
            companyId={companyId}
            closureCount={closureCount}
            onPreferenceComplete={handleBannerClosureComplete}
          />
        )}
      </>
    );
  };
};

const USBBanner: React.FC<EnhancedBannerProps> = (props) => {
  const BannerComponent = createUSBBanner();
  return <BannerComponent {...props} />;
};

export default USBBanner;