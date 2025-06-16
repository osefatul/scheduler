// FIXED SecondClosurePopup.tsx - NEW APPROACH
import React, { useState, useCallback, useRef } from "react";
import USBModal, { ModalHeader, ModalBody, ModalFooter } from "@usb-shield/react-modal";
import Button from "@usb-shield/react-button";
import { businessOptions, dontShowAgainOptions } from "./BannerselectOptions";
import PopupCloseandShowBanner from "./PopupCloseandShow";
import SharedModal from '../../../../../packages/shared/src/utils/SharedBannerContent';
import { useSetClosurePreferenceMutation, useHandleGlobalOptOutMutation } from './campaignClosureAPI';
import { useSessionClosureManager } from './sessionClosureManager';

import {
  SecondBannerExternalBannerPopup,
} from "./SecondInsightBannerPopup.styles";

interface SecondClosurePopupProps {
  isOpen: boolean;
  handleClose: () => void;
  campaignId?: string;
  userId?: string;
  companyId?: string;
  closureCount?: number;
  onPreferenceComplete?: () => void;
}

const SecondClosurePopup: React.FC<SecondClosurePopupProps> = ({
  isOpen,
  handleClose,
  campaignId,
  userId,
  companyId,
  closureCount = 2,
  onPreferenceComplete
}) => {
  const [isSuccessPopupOpen, setSuccessPopupOpen] = useState(false);
  const [isSharedModalOpen, setSharedModalOpen] = useState(false);
  const [sharedModalOptions, setSharedModalOptions] = useState(businessOptions);
  const [modalType, setModalType] = useState<'campaign' | 'global'>('campaign');
  const [isProcessing, setIsProcessing] = useState(false);
  const [successMessage, setSuccessMessage] = useState("");
  
  // Track if we should trigger onPreferenceComplete after success popup closes
  const shouldCompleteOnSuccessClose = useRef(false);

  // API hooks
  const [setClosurePreference] = useSetClosurePreferenceMutation();
  const [handleGlobalOptOut] = useHandleGlobalOptOutMutation();
  const { recordClosure } = useSessionClosureManager();

  // Handle "Close now but show in future"
  const handleCloseButShowInFuture = useCallback(async () => {
    setSharedModalOptions(businessOptions);
    setModalType('campaign');
    setSharedModalOpen(true);
  }, []);

  // Handle "Stop showing this ad"
  const handleStopShowingAd = useCallback(() => {
    setSharedModalOptions(dontShowAgainOptions);
    setModalType('global');
    setSharedModalOpen(true);
  }, []);

  // Handle SharedModal onSubmit (called after onProceed)
  const handleSharedModalOnSubmit = useCallback(async () => {
    console.log('SharedModal complete - closing and showing success');
    setSharedModalOpen(false);
    
    // Close the main modal
    handleClose();
    
    // Show success popup
    setTimeout(() => {
      setSuccessPopupOpen(true);
      shouldCompleteOnSuccessClose.current = true;
    }, 100);
  }, [handleClose]);

  // Handle SharedModal onProceed
  const handleSharedModalSubmit = useCallback(async (selectedReason: string | null, additionalComments: string) => {
    if (!campaignId || !userId || !companyId) {
      console.error('Missing required parameters');
      setSuccessMessage("Your preference has been updated.");
      return;
    }

    setIsProcessing(true);

    try {
      const reason = selectedReason || additionalComments || "User provided feedback";

      if (modalType === 'campaign') {
        // "Close now but show in future"
        await setClosurePreference({
          userId,
          companyId,
          campaignId,
          wantsToSee: true,
          reason,
          isGlobalResponse: true,
          preferenceDate: new Date().toISOString().split('T')[0]
        }).unwrap();

        console.log('Global preference set: User wants future campaigns after 1-month wait');
        recordClosure(campaignId, userId, companyId, closureCount, 'TEMPORARY_CLOSE_SESSION');
        
        setSuccessMessage("You won't see campaigns for 1 month. Future campaigns will be shown after the waiting period.");

      } else {
        // "Stop showing this ad" - Global opt-out
        await setClosurePreference({
          userId,
          companyId,
          campaignId,
          wantsToSee: false,
          reason,
          isGlobalResponse: true,
          preferenceDate: new Date().toISOString().split('T')[0]
        }).unwrap();

        console.log('Global preference set: Complete opt out');
        recordClosure(campaignId, userId, companyId, closureCount, 'GLOBAL_OPT_OUT');
        setSuccessMessage("You have been opted out of all future insights and campaigns.");
      }

    } catch (error) {
      console.error('Error setting preference:', error);
      setSuccessMessage("Your preference has been updated.");
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, modalType, setClosurePreference, recordClosure]);

  // Handle SharedModal close
  const handleSharedModalClose = useCallback(() => {
    setSharedModalOpen(false);
  }, []);

  // Handle success popup close
  const handleSuccessPopupClose = useCallback(() => {
    console.log('Success popup closing');
    setSuccessPopupOpen(false);
    
    // Check if we should complete the preference flow
    if (shouldCompleteOnSuccessClose.current && onPreferenceComplete) {
      console.log('✅ Calling onPreferenceComplete to hide banner');
      onPreferenceComplete();
      shouldCompleteOnSuccessClose.current = false;
    }
  }, [onPreferenceComplete]);

  return (
    <>
      <SecondBannerExternalBannerPopup>
        <USBModal handleClose={handleClose} id="second-closure-modal" isOpen={isOpen}>
          <ModalHeader id="second-closure-header">
            Close banner?
          </ModalHeader>
          <ModalBody>
            You've closed similar ads recently. Want to stop seeing these?
          </ModalBody>
          <ModalFooter>
            <Button
              ctaStyle="standard"
              emphasis="heavy"
              handleClick={handleCloseButShowInFuture}
              disabled={isProcessing}
            >
              Close now but show in future
            </Button>
            <Button
              ctaStyle="standard"
              emphasis="subtle"
              handleClick={handleStopShowingAd}
              disabled={isProcessing}
            >
              Stop showing this ad
            </Button>
          </ModalFooter>
        </USBModal>
      </SecondBannerExternalBannerPopup>

      {/* Success popup */}
      <PopupCloseandShowBanner
        isOpen={isSuccessPopupOpen}
        handleClose={handleSuccessPopupClose}
        message={successMessage}
      />

      {/* Shared Modal */}
      <SharedModal
        isOpen={isSharedModalOpen}
        handleClose={handleSharedModalClose}
        headerText={
          modalType === 'campaign' 
            ? "Help us understand why you're closing this banner."
            : "Help us understand why you don't want to see these ads."
        }
        optionsArray={sharedModalOptions}
        onProceed={handleSharedModalSubmit}
        onSubmit={handleSharedModalOnSubmit}
      />
    </>
  );
};

export default SecondClosurePopup;