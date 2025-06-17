import React, { useState, useCallback } from "react";
import USBModal, {
  ModalHeader,
  ModalBody,
  ModalFooter,
} from "@usb-shield/react-modal";
import Button from "@usb-shield/react-button";
import { businessOptions, dontShowAgainOptions } from "./BannerselectOptions";
import PopupCloseandShowBanner from "./PopupCloseandShow";
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

const SecondClosurePopup: React.FC<SecondClosurePopupProps> = ({
  isOpen,
  handleClose,
  campaignId,
  userId,
  companyId,
  closureCount = 2,
  onPreferenceComplete,
}) => {
  const [isSuccessPopupOpen, setSuccessPopupOpen] = useState(false);
  const [isSharedModalOpen, setSharedModalOpen] = useState(false);
  const [sharedModalOptions, setSharedModalOptions] = useState(businessOptions);
  const [modalType, setModalType] = useState<"campaign" | "global">("campaign");
  const [isProcessing, setIsProcessing] = useState(false);
  const [successMessage, setSuccessMessage] = useState("");

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
    
    // Show success popup after closing banner
    setTimeout(() => {
      setSuccessPopupOpen(true);
    }, 100);
  }, [onPreferenceComplete, handleClose]);

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
        
        setSuccessMessage("Your preference has been updated.");
        setSuccessPopupOpen(true);
        return;
      }

      setIsProcessing(true);

      try {
        const reason =
          selectedReason || additionalComments || "User provided feedback";

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

          setSuccessMessage(
            "You won't see campaigns for 1 month. Future campaigns will be shown after the waiting period."
          );
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
          setSuccessMessage(
            "You have been opted out of all future insights and campaigns."
          );
        }

        // Close banner and modals IMMEDIATELY after API success
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setSharedModalOpen(false);
        handleClose();
        
        // Show success popup
        setSuccessPopupOpen(true);
      } catch (error) {
        console.error("Error setting preference:", error);

        // Even on error, close banner and modals
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setSharedModalOpen(false);
        handleClose();
        
        setSuccessMessage("Your preference has been updated.");
        setSuccessPopupOpen(true);
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
    ]
  );

  const handleSharedModalClose = useCallback(() => {
    setSharedModalOpen(false);
  }, []);

  const handleSuccessPopupClose = useCallback(() => {
    setSuccessPopupOpen(false);
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

      {/* Success confirmation popup - Shows AFTER banner is closed */}
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