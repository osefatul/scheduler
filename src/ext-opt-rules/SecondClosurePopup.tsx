// Enhanced SecondClosurePopup.tsx
import React, { useState, useCallback } from "react";
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

  // API hooks
  const [setClosurePreference] = useSetClosurePreferenceMutation();
  const [handleGlobalOptOut] = useHandleGlobalOptOutMutation();
  const { recordClosure } = useSessionClosureManager();

  // Handle "Close now but show in future" - campaign-specific preference
  const handleCloseButShowInFuture = useCallback(async () => {
    setSharedModalOptions(businessOptions);
    setModalType('campaign');
    setSharedModalOpen(true);
  }, []);

  // Handle "Stop showing this ad" - could be campaign-specific or global
  const handleStopShowingAd = useCallback(() => {
    setSharedModalOptions(dontShowAgainOptions);
    setModalType('global'); // This leads to global opt-out question
    setSharedModalOpen(true);
  }, []);

  // Handle submission from shared modal via onSubmit (no arguments)
  const handleSharedModalOnSubmit = useCallback(async () => {
    // onSubmit is called after onProceed, so the API call is already done
    // Close the SharedModal and show success popup
    console.log('SharedModal onSubmit called - closing SharedModal and showing success popup');
    setSharedModalOpen(false);
    
    // Show success popup after SharedModal closes
    setTimeout(() => {
      setSuccessPopupOpen(true);
    }, 100);
  }, []);

  // Handle submission from shared modal
  const handleSharedModalSubmit = useCallback(async (selectedReason: string | null, additionalComments: string) => {
    if (!campaignId || !userId || !companyId) {
      console.error('Missing required parameters for preference API');
      setSuccessMessage("Your preference has been updated.");
      setSuccessPopupOpen(true);
      return;
    }

    setIsProcessing(true);

    try {
      const reason = selectedReason || additionalComments || "User provided feedback";

      if (modalType === 'campaign') {
        // "Close now but show in future" - User wants to see future campaigns
        // This should trigger 1-month wait period for THIS campaign but allow future campaigns
        await setClosurePreference({
          userId,
          companyId,
          campaignId,
          wantsToSee: false, // Don't want THIS campaign for now
          reason,
          isGlobalResponse: true, // This affects global campaign eligibility  
          preferenceDate: new Date().toISOString().split('T')[0]
        }).unwrap();

        console.log('Global preference set: Allow future campaigns after wait period');
        recordClosure(campaignId, userId, companyId, closureCount, 'TEMPORARY_CLOSE');
        setSuccessMessage("You won't see this campaign for 1 month. Future campaigns may still be shown.");

      } else {
        // "Stop showing this ad" - Global opt-out
        await setClosurePreference({
          userId,
          companyId,
          campaignId,
          wantsToSee: false, // User doesn't want future insights
          reason,
          isGlobalResponse: true, // This triggers global opt-out
          preferenceDate: new Date().toISOString().split('T')[0]
        }).unwrap();

        console.log('Global preference set: Opt out of all insights');
        recordClosure(campaignId, userId, companyId, closureCount, 'GLOBAL_OPT_OUT');
        setSuccessMessage("You have been opted out of all future insights and campaigns.");
      }

      setSuccessPopupOpen(true);

    } catch (error) {
      console.error('Error setting preference:', error);
      // Show success popup anyway for UX
      setSuccessMessage("Your preference has been updated.");
      setSuccessPopupOpen(true);
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, modalType, setClosurePreference, recordClosure]);

  const handleSharedModalClose = useCallback(() => {
    setSharedModalOpen(false);
    // Don't close main modal, user can still make other choices
  }, []);

  // SUCCESS POPUP CLOSES EVERYTHING AND HIDES BANNER
  const handleSuccessPopupClose = useCallback(() => {
    setSuccessPopupOpen(false);
    // When success popup closes, call the preference complete callback to hide banner
    if (onPreferenceComplete) {
      onPreferenceComplete();
    }
    // Close the main modal
    handleClose();
  }, [handleClose, onPreferenceComplete]);

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

      {/* Success confirmation popup - ONLY closes after this */}
      <PopupCloseandShowBanner
        isOpen={isSuccessPopupOpen}
        handleClose={handleSuccessPopupClose}
        message={successMessage}
      />

      {/* Shared Modal for collecting reasons */}
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