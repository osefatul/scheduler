import React, { useState, useCallback } from "react";
import USBModal, {
  ModalHeader,
  ModalBody,
  ModalFooter,
} from "@usb-shield/react-modal";
import Button from "@usb-shield/react-button";
import { businessOptions, dontShowAgainOptions } from "./BannerselectOptions";
import SharedModal from "../../../../../packages/shared/src/utils/SharedBannerContent";

import { SecondBannerExternalBannerPopup } from "./SecondInsightBannerPopup.styles";
import { useSetClosurePreferenceMutation } from "@/external/services/campaignClosureAPI";
import { useSessionClosureManager } from "@/external/utils/sessionClosureManager";
import { useLocation } from "react-router-dom";

interface SecondClosurePopupProps {
  isOpen: boolean;
  handleClose: () => void;
  campaignId?: string;
  userId?: string;
  companyId?: string;
  closureCount?: number;
  onPreferenceComplete?: () => void;
}

// Use the same global state as FirstClosurePopup
declare global {
  var globalSuccessPopupState: {
    isOpen: boolean;
    message: string;
    onClose: () => void;
  };
}

const SecondClosurePopup: React.FC<SecondClosurePopupProps> = ({
  isOpen,
  handleClose,
  campaignId,
  userId,
  companyId,
  closureCount = 2,
  onPreferenceComplete,
}) => {
  const [isSharedModalOpen, setSharedModalOpen] = useState(false);
  const [sharedModalOptions, setSharedModalOptions] = useState(businessOptions);
  const [modalType, setModalType] = useState<"campaign" | "global">("campaign");
  const [isProcessing, setIsProcessing] = useState(false);

  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const dateParam = queryParams.get("date");
  let effectiveDate: Date;
  if (dateParam && /^\d{8}$/.test(dateParam)) {
    const formatted = `${dateParam.slice(0, 4)}-${dateParam.slice(4, 6)}-${dateParam.slice(6, 8)}`;
    effectiveDate = new Date(formatted);
  } else if (dateParam && !isNaN(Date.parse(dateParam))) {
    effectiveDate = new Date(dateParam);
  } else {
    effectiveDate = new Date();
  }

  const [setClosurePreference] = useSetClosurePreferenceMutation();
  const { recordClosure } = useSessionClosureManager();

  const showGlobalSuccessPopup = useCallback((message: string) => {
    // Use global state to show success popup outside banner hierarchy
    if (typeof window !== 'undefined') {
      if (!window.globalSuccessPopupState) {
        window.globalSuccessPopupState = {
          isOpen: false,
          message: "",
          onClose: () => {}
        };
      }
      window.globalSuccessPopupState.isOpen = true;
      window.globalSuccessPopupState.message = message;
      window.globalSuccessPopupState.onClose = () => {
        // Additional cleanup if needed
      };
    }
  }, []);

  const handleCloseButShowInFuture = useCallback(async () => {
    setSharedModalOptions(businessOptions);
    setModalType("campaign");
    setSharedModalOpen(true);
  }, []);

  const handleStopShowingAd = useCallback(() => {
    setSharedModalOptions(dontShowAgainOptions);
    setModalType("global");
    setSharedModalOpen(true);
  }, []);

  const handleSharedModalOnSubmit = useCallback(async () => {
    setSharedModalOpen(false);
    
    // Close banner and main modal immediately
    if (onPreferenceComplete) {
      onPreferenceComplete();
    }
    handleClose();
    
    // Show success popup after closing banner using global state
    setTimeout(() => {
      showGlobalSuccessPopup("Your preference has been updated.");
    }, 150);
  }, [onPreferenceComplete, handleClose, showGlobalSuccessPopup]);

  const handleSharedModalSubmit = useCallback(
    async (selectedReason: string | null, additionalComments: string) => {
      if (!campaignId || !userId || !companyId) {
        console.error("Missing required parameters for preference API");
        
        // Close banner and modals immediately even without API
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setSharedModalOpen(false);
        handleClose();
        
        setTimeout(() => {
          showGlobalSuccessPopup("Your preference has been updated.");
        }, 150);
        return;
      }

      setIsProcessing(true);

      try {
        const reason =
          selectedReason || additionalComments || "User provided feedback";

        let successMessage = "";

        if (modalType === "campaign") {
          await setClosurePreference({
            userId,
            companyId,
            campaignId,
            wantsToSee: true,
            reason,
            isGlobalResponse: true,
            preferenceDate: effectiveDate.toISOString().split('T')[0]
          }).unwrap();

          recordClosure(
            campaignId,
            userId,
            companyId,
            closureCount,
            "TEMPORARY_CLOSE_SESSION"
          );

          successMessage = "You won't see campaigns for 1 month. Future campaigns will be shown after the waiting period.";
        } else {
          await setClosurePreference({
            userId,
            companyId,
            campaignId,
            wantsToSee: false,
            reason,
            isGlobalResponse: true,
            preferenceDate: effectiveDate.toISOString().split('T')[0]
          }).unwrap();

          recordClosure(
            campaignId,
            userId,
            companyId,
            closureCount,
            "GLOBAL_OPT_OUT"
          );
          successMessage = "You have been opted out of all future insights and campaigns.";
        }

        // Close banner and modals IMMEDIATELY after API success
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setSharedModalOpen(false);
        handleClose();
        
        // Show success popup using global state
        setTimeout(() => {
          showGlobalSuccessPopup(successMessage);
        }, 150);
      } catch (error) {
        console.error("Error setting preference:", error);

        // Even on error, close banner and modals
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setSharedModalOpen(false);
        handleClose();
        
        setTimeout(() => {
          showGlobalSuccessPopup("Your preference has been updated.");
        }, 150);
      } finally {
        setIsProcessing(false);
      }
    },
    [
      campaignId,
      userId,
      companyId,
      closureCount,
      modalType,
      setClosurePreference,
      recordClosure,
      onPreferenceComplete,
      handleClose,
      showGlobalSuccessPopup,
    ]
  );

  const handleSharedModalClose = useCallback(() => {
    setSharedModalOpen(false);
  }, []);

  return (
    <>
      <SecondBannerExternalBannerPopup>
        <USBModal
          handleClose={handleClose}
          id="second-closure-modal"
          isOpen={isOpen}
        >
          <ModalHeader id="second-closure-header">Close banner?</ModalHeader>
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

      {/* Shared Modal for collecting reasons */}
      <SharedModal
        isOpen={isSharedModalOpen}
        handleClose={handleSharedModalClose}
        headerText={
          modalType === "campaign"
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