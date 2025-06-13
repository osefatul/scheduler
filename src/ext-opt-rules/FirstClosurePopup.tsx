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
      return;
    }

    setIsProcessing(true);

    try {
      await setClosurePreference({
        userId,
        companyId,
        campaignId,
        wantsToSee: true,           // ✅ User WANTS to see this campaign again
        reason: "User chose to close temporarily",
        isGlobalResponse: false,    // ✅ This is campaign-specific, not global
        preferenceDate: new Date().toISOString().split('T')[0]
      }).unwrap();

      console.log('Preference set: Close & show later');
      
      // Record as temporary close for current session
      recordClosure(campaignId, userId, companyId, closureCount, 'TEMPORARY_CLOSE_SESSION');
      
      if (onPreferenceComplete) {
        onPreferenceComplete();
      }
      
      setSuccessMessage("The banner has been closed for now and will be shown to you in a future session.");
      setSuccessPopupOpen(true);
    } catch (error) {
      console.error('Error setting preference:', error);
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, setClosurePreference, recordClosure, onPreferenceComplete]);

  // Handle "Don't show again" click - open reasons modal
  const handleDontShowAgainClick = useCallback(() => {
    setDontShowAgainPopupOpen(true);
  }, []);

  // Handle submission from "Don't show again" modal via onSubmit (no arguments)
  const handleDontShowAgainOnSubmit = useCallback(async () => {
    // onSubmit is called after onProceed, so the API call is already done
    // Close the SharedModal and show success popup
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
      setSuccessMessage("We won't show you this banner again.");
      setSuccessPopupOpen(true);
      return;
    }

    setIsProcessing(true);

    try {
      const reason = selectedReason || additionalComments || "User chose not to see campaign again";
      
      console.log('=== DONT SHOW AGAIN REQUEST ===');
      console.log('Parameters:', {
        userId,
        companyId, 
        campaignId,
        wantsToSee: false,          // ✅ User doesn't want to see this campaign
        reason,
        isGlobalResponse: false,    // ✅ This is campaign-specific, not global
        preferenceDate: new Date().toISOString().split('T')[0]
      });
      
      await setClosurePreference({
        userId,
        companyId,
        campaignId,
        wantsToSee: false,          // ✅ CRITICAL: User doesn't want to see this campaign
        reason,
        isGlobalResponse: false,    // ✅ CRITICAL: This is campaign-specific
        preferenceDate: new Date().toISOString().split('T')[0]
      }).unwrap();

      console.log('Preference set: Don\'t show again', reason);
      
      // Update session - campaign is permanently blocked
      recordClosure(campaignId, userId, companyId, closureCount, 'PERMANENT_BLOCK');
      
      // CRITICAL: Immediately notify parent to hide banner
      if (onPreferenceComplete) {
        onPreferenceComplete();
      }
      
      setSuccessMessage("We won't show you this banner again.");
      setSuccessPopupOpen(true);
    } catch (error) {
      console.error('Error setting preference:', error);
      // Show success popup anyway for UX
      setSuccessMessage("We won't show you this banner again.");
      setSuccessPopupOpen(true);
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, setClosurePreference, recordClosure, onPreferenceComplete]);

  // SUCCESS POPUP CLOSES EVERYTHING 
  const handleSuccessPopupClose = useCallback(() => {
    console.log('Success popup closing');
    setSuccessPopupOpen(false);
    
    // Close the main modal
    console.log('Closing first closure modal');
    handleClose();
  }, [handleClose]);

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

      {/* Success confirmation popup - ONLY closes after this */}
      <PopupCloseandShowBanner
        isOpen={isSuccessPopupOpen}
        handleClose={handleSuccessPopupClose}
        message={successMessage}
      />

      {/* Don't show again reasons modal */}
      <SharedModal
        isOpen={isDontShowAgainPopupOpen}
        handleClose={handleDontShowAgainPopupClose}
        headerText="We won't show you the banner again."
        optionsArray={businessOptions}
        onProceed={handleDontShowAgainSubmit}
        onSubmit={handleDontShowAgainOnSubmit}
      />
    </>
  );
};

export default FirstClosurePopup;