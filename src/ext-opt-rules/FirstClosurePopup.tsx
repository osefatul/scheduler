// Enhanced FirstClosurePopup.tsx
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
}

const FirstClosurePopup: React.FC<FirstClosurePopupProps> = ({
  isOpen,
  handleClose,
  campaignId,
  userId,
  companyId,
  closureCount = 1
}) => {
  const [isSuccessPopupOpen, setSuccessPopupOpen] = useState(false);
  const [isDontShowAgainPopupOpen, setDontShowAgainPopupOpen] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

  // API hooks
  const [setClosurePreference] = useSetClosurePreferenceMutation();
  const { recordClosure } = useSessionClosureManager();

  // Handle "Close & show later" - this means user wants to see campaign again
  const handleCloseAndShowLater = useCallback(async () => {
    if (!campaignId || !userId || !companyId) {
      console.error('Missing required parameters for preference API');
      setSuccessPopupOpen(true);
      return;
    }

    setIsProcessing(true);

    try {
      await setClosurePreference({
        userId,
        companyId,
        campaignId,
        wantsToSee: true, // User wants to see this campaign again
        reason: "User chose to close temporarily",
        isGlobalResponse: false, // This is campaign-specific
        preferenceDate: new Date().toISOString().split('T')[0]
      }).unwrap();

      console.log('Preference set: Close & show later');
      
      // Update session - campaign remains eligible for future sessions
      recordClosure(campaignId, userId, companyId, closureCount, 'TEMPORARY_CLOSE');
      
      setSuccessPopupOpen(true);
    } catch (error) {
      console.error('Error setting preference:', error);
      // Show success popup anyway for UX
      setSuccessPopupOpen(true);
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, setClosurePreference, recordClosure]);

  // Handle "Don't show again" click - open reasons modal
  const handleDontShowAgainClick = useCallback(() => {
    setDontShowAgainPopupOpen(true);
  }, []);

  // Handle submission from "Don't show again" modal
  const handleDontShowAgainSubmit = useCallback(async (selectedReason: string | null, additionalComments: string) => {
    if (!campaignId || !userId || !companyId) {
      console.error('Missing required parameters for preference API');
      setDontShowAgainPopupOpen(false);
      setSuccessPopupOpen(true);
      return;
    }

    setIsProcessing(true);

    try {
      const reason = selectedReason || additionalComments || "User chose not to see campaign again";
      
      await setClosurePreference({
        userId,
        companyId,
        campaignId,
        wantsToSee: false, // User doesn't want to see this campaign again
        reason,
        isGlobalResponse: false, // This is campaign-specific
        preferenceDate: new Date().toISOString().split('T')[0]
      }).unwrap();

      console.log('Preference set: Don\'t show again', reason);
      
      // Update session - campaign is permanently blocked
      recordClosure(campaignId, userId, companyId, closureCount, 'PERMANENT_BLOCK');
      
      setDontShowAgainPopupOpen(false);
      setSuccessPopupOpen(true);
    } catch (error) {
      console.error('Error setting preference:', error);
      // Show success popup anyway for UX
      setDontShowAgainPopupOpen(false);
      setSuccessPopupOpen(true);
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, setClosurePreference, recordClosure]);

  const handleSuccessPopupClose = useCallback(() => {
    setSuccessPopupOpen(false);
    handleClose();
  }, [handleClose]);

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

      {/* Success confirmation popup */}
      <PopupCloseandShowBanner
        isOpen={isSuccessPopupOpen}
        handleClose={handleSuccessPopupClose}
        message="The banner has been closed for now and will be shown to you in a future session."
      />

      {/* Don't show again reasons modal */}
      <SharedModal
        isOpen={isDontShowAgainPopupOpen}
        handleClose={handleDontShowAgainPopupClose}
        headerText="We won't show you the banner again."
        optionsArray={businessOptions}
        onProceed={handleDontShowAgainSubmit}
        onSubmit={() => handleDontShowAgainSubmit(null, "")}
      />
    </>
  );
};

export default FirstClosurePopup;