import React, { useState, useCallback } from "react";
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

  // API hooks
  const [setClosurePreference] = useSetClosurePreferenceMutation();
  const { recordClosure } = useSessionClosureManager();

  // Handle "Close & show later" - this means user wants to see campaign again
  const handleCloseAndShowLater = useCallback(async () => {
    if (!campaignId || !userId || !companyId) {
      console.error('Missing required parameters for preference API');
      setSuccessMessage("The banner has been closed for now and will be shown to you in a future session.");
      setSuccessPopupOpen(true);
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
      
      // Record as TEMPORARY_CLOSE for current session
      recordClosure(campaignId, userId, companyId, closureCount, 'TEMPORARY_CLOSE_SESSION');
      
      // Close the first modal first
      handleClose();
      
      // Show success message
      setSuccessMessage("The banner has been closed for now and will be shown to you in a future session.");
      setSuccessPopupOpen(true);
    } catch (error) {
      console.error('Error setting preference:', error);
      handleClose();
      setSuccessMessage("The banner has been closed for now and will be shown to you in a future session.");
      setSuccessPopupOpen(true);
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, setClosurePreference, recordClosure, handleClose]);

  // Handle "Don't show again" click - open reasons modal
  const handleDontShowAgainClick = useCallback(() => {
    setDontShowAgainPopupOpen(true);
  }, []);

  // Handle submission from "Don't show again" modal via onSubmit
  const handleDontShowAgainOnSubmit = useCallback(async () => {
    console.log('SharedModal onSubmit called - closing SharedModal and showing success popup');
    setDontShowAgainPopupOpen(false);
    
    // Show success popup after SharedModal closes
    setTimeout(() => {
      setSuccessPopupOpen(true);
    }, 100);
  }, []);

  // Handle submission from "Don't show again" modal
  const handleDontShowAgainSubmit = useCallback(async (selectedReason: string | null, additionalComments: string) => {
    if (!campaignId || !userId || !companyId) {
      console.error('Missing required parameters for preference API');
      setDontShowAgainPopupOpen(false);
      setSuccessMessage("We won't show you this banner again for 1 month.");
      setSuccessPopupOpen(true);
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

      console.log('Preference set: Don\'t show again', reason);
      
      // Update session - campaign is blocked for 1 month
      recordClosure(campaignId, userId, companyId, closureCount, 'ONE_MONTH_WAIT');
      
      // Close the first modal
      handleClose();
      
      setSuccessMessage("We won't show you this banner again for 1 month.");
      // Success popup will be shown by onSubmit callback
    } catch (error) {
      console.error('Error setting preference:', error);
      handleClose();
      setSuccessMessage("We won't show you this banner again for 1 month.");
      // Success popup will be shown by onSubmit callback
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, setClosurePreference, recordClosure, handleClose]);

  // Handle success popup close - This is when banner should disappear
  const handleSuccessPopupClose = useCallback(() => {
    console.log('Success popup closing - NOW hiding banner');
    setSuccessPopupOpen(false);
    
    // Only NOW call onPreferenceComplete to hide banner
    if (onPreferenceComplete) {
      console.log('Calling onPreferenceComplete to hide banner');
      onPreferenceComplete();
    }
  }, [onPreferenceComplete]);

  const handleDontShowAgainPopupClose = useCallback(() => {
    setDontShowAgainPopupOpen(false);
    // Don't close main modal, user can still choose "Close & show later"
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

      {/* Success confirmation popup - Banner only hides when this closes */}
      <PopupCloseandShowBanner
        isOpen={isSuccessPopupOpen}
        handleClose={handleSuccessPopupClose}
        message={successMessage}
      />

      {/* Don't show again reasons modal */}
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