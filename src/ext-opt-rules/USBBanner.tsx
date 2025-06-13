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
    const { isCampaignClosed, recordClosure, hasUserMadePreferenceChoice, sessionClosures } = useSessionClosureManager();

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

          // CORRECTED: Always record closure for session tracking
          recordClosure(
            campaignId,
            userId,
            companyId,
            closureData.closureCount,
            closureData.action || 'CLOSURE_RECORDED'
          );

          return closureData;
        }
      } catch (error) {
        console.error('Error calling closure API:', error);
        return null;
      }
    }, [campaignId, userId, companyId, closeInsight, recordClosure]);

    // Handle close icon click
    const handleCloseIconClick = useCallback(async () => {
      if (isClosingInsight) return; // Prevent multiple clicks
    
      console.log('Close icon clicked, calling closure API...');
    
      // Call closure API first
      const closureResponse = await handleClosureAPI();
      
      if (!closureResponse) {
        console.error('Closure API call failed');
        return;
      }
    
      console.log('Closure API response:', closureResponse);
    
      // NEW LOGIC: Use closure count and action from backend
      if (closureResponse.closureCount === 1 && 
          closureResponse.action === "RECORDED_FIRST_CLOSURE") {
        
        console.log('TRUE FIRST CLOSURE - hiding banner immediately');
        // This is the very first closure according to backend database
        setIsHidden(true);
        recordClosure(campaignId, userId, companyId, 1, 'FIRST_CLOSURE_HIDE');
        
        if (onBannerClosed && campaignId) {
          onBannerClosed(campaignId, 1);
        }
        
      } else if (closureResponse.closureCount >= 2 || 
                 closureResponse.requiresUserInput === true) {
        
        console.log('CLOSURE COUNT >= 2 OR REQUIRES INPUT - showing modal');
        // Second closure or subsequent - show appropriate modal
        
        if (closureResponse.isGlobalPrompt === true) {
          console.log('Showing second modal for global preference');
          setIsSecondModalOpen(true);
        } else {
          console.log('Showing first modal for campaign preference');
          setIsFirstModalOpen(true);
        }
        
        setIsHidden(false); // Keep banner visible while modal is open
        
      } else {
        
        console.log('UNEXPECTED CASE - showing first modal as fallback');
        // Fallback - show first modal for any unexpected case
        setIsFirstModalOpen(true);
        setIsHidden(false);
      }
    
    }, [isClosingInsight, handleClosureAPI, campaignId, userId, companyId, recordClosure, onBannerClosed]);

    // Handle banner closure callback - called when user completes preference flow
    const handleBannerClosureComplete = useCallback(() => {
      console.log('User completed preference flow, hiding banner IMMEDIATELY');
      
      // IMMEDIATE: Hide banner at USBBanner level
      setIsHidden(true);
      setIsFirstModalOpen(false);
      setIsSecondModalOpen(false);
      
      // IMMEDIATE: Force parent to hide banner too
      if (onBannerClosed && campaignId) {
        console.log('Calling onBannerClosed to hide parent banner');
        onBannerClosed(campaignId, closureCount);
      }
      
      // Debug session state
      if (campaignId && userId && companyId) {
        setTimeout(() => {
          const isClosedNow = isCampaignClosed(campaignId, userId, companyId);
          console.log('Session state check after closure:', isClosedNow);
        }, 100);
      }
    }, [onBannerClosed, campaignId, closureCount, isCampaignClosed, userId, companyId]);

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

    // Don't render if banner should be hidden AND no modals are open
    if (isHidden && !isFirstModalOpen && !isSecondModalOpen) {
      console.log('Banner is hidden and no modals open - not rendering');
      return null;
    }

    // ALWAYS render banner if any modal is open, regardless of isHidden state
    const shouldShowBanner = !isHidden || isFirstModalOpen || isSecondModalOpen;
    console.log('Banner render decision:', { 
      isHidden, 
      isFirstModalOpen, 
      isSecondModalOpen, 
      shouldShowBanner 
    });

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