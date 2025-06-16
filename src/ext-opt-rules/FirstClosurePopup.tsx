// FIXED FirstClosurePopup.tsx - NEW APPROACH
import React, { useState, useCallback, useRef } from "react";
import USBModal, { ModalHeader, ModalBody, ModalFooter } from "@usb-shield/react-modal";
import Button from "@usb-shield/react-button";
import { businessOptions } from "./BannerselectOptions";
import PopupCloseandShowBanner from "./PopupCloseandShow";
import SharedModal from '../../../../../packages/shared/src/utils/SharedBannerContent';
import { useSetClosurePreferenceMutation } from './campaignClosureAPI';
import { useSessionClosureManager } from './sessionClosureManager';

import {
  ExternalBannerPopup
} from "./bannerpopup.styles";

interface FirstClosurePopupProps {
  isOpen: boolean;
  handleClose: () => void;
  campaignId?: string;
  userId?: string;
  companyId?: string;
  closureCount?: number;
  onPreferenceComplete?: () => void;
}

const FirstClosurePopup: React.FC<FirstClosurePopupProps> = ({
  isOpen,
  handleClose,
  campaignId,
  userId,
  companyId,
  closureCount = 1,
  onPreferenceComplete
}) => {
  const [isSuccessPopupOpen, setSuccessPopupOpen] = useState(false);
  const [isDontShowAgainPopupOpen, setDontShowAgainPopupOpen] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [successMessage, setSuccessMessage] = useState("");
  
  // Track if we should trigger onPreferenceComplete after success popup closes
  const shouldCompleteOnSuccessClose = useRef(false);

  // API hooks
  const [setClosurePreference] = useSetClosurePreferenceMutation();
  const { recordClosure } = useSessionClosureManager();

  // Handle "Close & show later"
  const handleCloseAndShowLater = useCallback(async () => {
    if (!campaignId || !userId || !companyId) {
      console.error('Missing required parameters');
      // Still show success for UX
      setSuccessMessage("The banner has been closed for now and will be shown to you in a future session.");
      handleClose(); // Close the modal
      setTimeout(() => {
        setSuccessPopupOpen(true);
        shouldCompleteOnSuccessClose.current = true;
      }, 100);
      return;
    }

    setIsProcessing(true);

    try {
      await setClosurePreference({
        userId,
        companyId,
        campaignId,
        wantsToSee: true,
        reason: "User chose to close temporarily",
        isGlobalResponse: false,
        preferenceDate: new Date().toISOString().split('T')[0]
      }).unwrap();

      console.log('Preference set: Close & show later');
      
      // Record closure in session
      recordClosure(campaignId, userId, companyId, closureCount, 'TEMPORARY_CLOSE_SESSION');
      
      setSuccessMessage("The banner has been closed for now and will be shown to you in a future session.");
      
      // Close the modal first
      handleClose();
      
      // Show success popup after a small delay
      setTimeout(() => {
        setSuccessPopupOpen(true);
        shouldCompleteOnSuccessClose.current = true;
      }, 100);
      
    } catch (error) {
      console.error('Error setting preference:', error);
      // Still show success for UX
      setSuccessMessage("The banner has been closed for now and will be shown to you in a future session.");
      handleClose();
      setTimeout(() => {
        setSuccessPopupOpen(true);
        shouldCompleteOnSuccessClose.current = true;
      }, 100);
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, setClosurePreference, recordClosure, handleClose]);

  // Handle "Don't show again" click
  const handleDontShowAgainClick = useCallback(() => {
    setDontShowAgainPopupOpen(true);
  }, []);

  // Handle SharedModal onSubmit (called after onProceed)
  const handleDontShowAgainOnSubmit = useCallback(() => {
    console.log('SharedModal complete - closing and showing success');
    setDontShowAgainPopupOpen(false);
    
    // Close the main modal
    handleClose();
    
    // Show success popup
    setTimeout(() => {
      setSuccessPopupOpen(true);
      shouldCompleteOnSuccessClose.current = true;
    }, 100);
  }, [handleClose]);

  // Handle SharedModal onProceed
  const handleDontShowAgainSubmit = useCallback(async (selectedReason: string | null, additionalComments: string) => {
    if (!campaignId || !userId || !companyId) {
      console.error('Missing required parameters');
      setSuccessMessage("We won't show you this banner again for 1 month.");
      return;
    }

    setIsProcessing(true);

    try {
      const reason = selectedReason || additionalComments || "User chose not to see campaign for 1 month";
      
      await setClosurePreference({
        userId,
        companyId,
        campaignId,
        wantsToSee: false,
        reason,
        isGlobalResponse: false,
        preferenceDate: new Date().toISOString().split('T')[0]
      }).unwrap();

      console.log('Preference set: Don\'t show again for 1 month');
      
      // Record closure
      recordClosure(campaignId, userId, companyId, closureCount, 'ONE_MONTH_WAIT');
      
      setSuccessMessage("We won't show you this banner again for 1 month.");
      
    } catch (error) {
      console.error('Error setting preference:', error);
      setSuccessMessage("We won't show you this banner again for 1 month.");
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, setClosurePreference, recordClosure]);

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

  // Handle SharedModal close
  const handleDontShowAgainPopupClose = useCallback(() => {
    setDontShowAgainPopupOpen(false);
  }, []);

  return (
    <>
      <ExternalBannerPopup>
        <USBModal handleClose={handleClose} id="first-closure-modal" isOpen={isOpen}>
          <ModalHeader id="first-closure-header">
            Close banner?
          </ModalHeader>
          <ModalBody>
            The banner will be closed for now but you can choose to see it later.
          </ModalBody>
          <ModalFooter>
            <Button
              ctaStyle="standard"
              emphasis="heavy"
              handleClick={handleCloseAndShowLater}
              disabled={isProcessing}
            >
              {isProcessing ? 'Processing...' : 'Close & show later'}
            </Button>
            <Button
              ctaStyle="standard"
              emphasis="subtle"
              handleClick={handleDontShowAgainClick}
              disabled={isProcessing}
            >
              Don't show again
            </Button>
          </ModalFooter>
        </USBModal>
      </ExternalBannerPopup>

      {/* Success popup */}
      <PopupCloseandShowBanner
        isOpen={isSuccessPopupOpen}
        handleClose={handleSuccessPopupClose}
        message={successMessage}
      />

      {/* Don't show again modal */}
      <SharedModal
        isOpen={isDontShowAgainPopupOpen}
        handleClose={handleDontShowAgainPopupClose}
        headerText="We won't show you the banner again for 1 month."
        optionsArray={businessOptions}
        onProceed={handleDontShowAgainSubmit}
        onSubmit={handleDontShowAgainOnSubmit}
      />
    </>
  );
};

export default FirstClosurePopup;