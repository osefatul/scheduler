// Enhanced USBBanner.tsx - COMPLETE UPDATED VERSION WITH ALL FIXES
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
  BannercloseIcon
} from "./USBBanner.styled";

import { USBIconClose } from '@usb-shield/react-icons';
import { useCloseInsightMutation } from './campaignClosureAPI';
import { useSessionClosureManager } from './sessionClosureManager';

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
    const [isHidden, setIsHidden] = useState(false);
    const [closureCount, setClosureCount] = useState(0);

    // API and session management hooks
    const [closeInsight, { isLoading: isClosingInsight }] = useCloseInsightMutation();
    const { isCampaignClosed, recordClosure } = useSessionClosureManager();

    // Check if banner should be hidden on mount
    useEffect(() => {
      if (campaignId && userId && companyId) {
        const isClosedInSession = isCampaignClosed(campaignId, userId, companyId);
        if (isClosedInSession) {
          console.log(`Banner ${campaignId} is already closed in this session`);
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

    // Handle closure API call
    const handleClosureAPI = useCallback(async () => {
      if (!campaignId || !userId || !companyId) {
        console.error('Missing required params for closure API:', { campaignId, userId, companyId });
        return null;
      }

      try {
        console.log(`Calling closure API for campaign ${campaignId}`);
        const response = await closeInsight({
          userId,
          companyId,
          campaignId,
          closureDate: new Date().toISOString().split('T')[0] // YYYY-MM-DD format
        }).unwrap();

        console.log('Closure API response:', response);

        if (response.success && response.data) {
          const closureData = response.data;
          setClosureCount(closureData.closureCount);

          // Record closure in session
          recordClosure(
            campaignId,
            userId,
            companyId,
            closureData.closureCount,
            closureData.action
          );

          // Notify parent component
          if (onBannerClosed) {
            onBannerClosed(campaignId, closureData.closureCount);
          }

          return closureData;
        }
      } catch (error) {
        console.error('Error calling closure API:', error);
        return null;
      }
    }, [campaignId, userId, companyId, closeInsight, recordClosure, onBannerClosed]);

    // Handle close icon click
    const handleCloseIconClick = useCallback(async () => {
      if (isClosingInsight) return; // Prevent multiple clicks

      console.log('Close icon clicked, calling closure API...');

      // Call closure API first - DON'T hide banner yet
      const closureResponse = await handleClosureAPI();
      
      if (!closureResponse) {
        console.error('Closure API call failed');
        // If API call failed, keep banner visible
        return;
      }

      console.log('Closure API response:', closureResponse);

      // ALWAYS show modal if we get a closure response - let user make choice
      // Determine which modal to show based on closure count and action
      if (closureResponse.closureCount === 1) {
        console.log('First closure - showing first modal');
        setIsFirstModalOpen(true);
      } else if (closureResponse.closureCount === 2) {
        // Second closure - check if it's global or campaign specific
        if (closureResponse.isGlobalPrompt || closureResponse.action === 'PROMPT_GLOBAL_PREFERENCE') {
          console.log('Second closure with global prompt - showing second modal');
          setIsSecondModalOpen(true);
        } else {
          console.log('Second closure with campaign prompt - showing first modal');
          setIsFirstModalOpen(true);
        }
      } else {
        // Fallback for any other case - show first modal
        console.log('Fallback case - showing first modal');
        setIsFirstModalOpen(true);
      }
      
      // Banner stays visible until user completes the flow
      setIsHidden(false);

    }, [isClosingInsight, handleClosureAPI]);

    // Handle banner closure callback - called when user completes preference flow
    const handleBannerClosureComplete = useCallback(() => {
      console.log('User completed preference flow, hiding banner');
      setIsHidden(true);
      setIsFirstModalOpen(false);
      setIsSecondModalOpen(false);
      
      // Notify parent component
      if (onBannerClosed && campaignId) {
        onBannerClosed(campaignId, closureCount);
      }
    }, [onBannerClosed, campaignId, closureCount]);

    // Handle modal closures - Banner only hides when user completes the preference flow
    const handleFirstModalClose = useCallback(() => {
      setIsFirstModalOpen(false);
      // DON'T hide banner here - let the success popup handle banner hiding
      // Banner stays visible until user completes preference selection
    }, []);

    const handleSecondModalClose = useCallback(() => {
      setIsSecondModalOpen(false);
      // DON'T hide banner here - let the success popup handle banner hiding
      // Banner stays visible until user completes preference selection
    }, []);

    // Don't render if banner should be hidden
    if (isHidden && !isFirstModalOpen && !isSecondModalOpen) {
      return null;
    }

    return (
      <>
        {!isHidden && (
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
                cursor: isClosingInsight ? 'not-allowed' : 'pointer',
                opacity: isClosingInsight ? 0.5 : 1
              }}
            >
              <USBIconClose colorVariant={isMuted ? 'light' : 'default'} />
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

        {/* Modals */}
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