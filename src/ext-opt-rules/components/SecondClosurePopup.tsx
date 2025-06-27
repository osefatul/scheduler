
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
  const [hasSubmitted, setHasSubmitted] = useState(false);

  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const dateParam = queryParams.get("date");
  let effectiveDate: Date;
  if (dateParam && /^\d{8}$/.test(dateParam)) {
    const formatted = `${dateParam.slice(0, 4)}-${dateParam.slice(
      4,
      6
    )}-${dateParam.slice(6, 8)}`;
    effectiveDate = new Date(formatted);
  } else if (dateParam && !isNaN(Date.parse(dateParam))) {
    effectiveDate = new Date(dateParam);
  } else {
    effectiveDate = new Date();
  }

  const [setClosurePreference] = useSetClosurePreferenceMutation();
  const { recordClosure } = useSessionClosureManager();

  const showGlobalSuccessPopup = useCallback((message: string) => {
    // Use the same global state variable as FirstClosurePopup
    if (typeof window !== "undefined") {
      // Make sure we're using the same global variable name as FirstClosurePopup
      if (!window.globalSuccessPopupState) {
        window.globalSuccessPopupState = {
          isOpen: false,
          message: "",
          onClose: () => {},
        };
      }

      window.globalSuccessPopupState.isOpen = true;
      window.globalSuccessPopupState.message = message;
      window.globalSuccessPopupState.onClose = () => {};

      // Force a state update by dispatching a custom event
      window.dispatchEvent(new CustomEvent("globalSuccessPopupStateChanged"));
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

  const handleSharedModalSubmit = useCallback(
    async (selectedReason: string | null, additionalComments: string) => {
      if (hasSubmitted || isProcessing) {
        console.warn(
          "Form already submitted or processing, ignoring further submissions."
        );
        return;
      }

      if (!campaignId || !userId || !companyId) {
        console.error("Missing required parameters for preference API");

        // Close banner and modals immediately even without API
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setSharedModalOpen(false);
        handleClose();

        setTimeout(() => {
          showGlobalSuccessPopup(
            "We won’t show you any banners in the future."
          );
        }, 150);
        return;
      }

      setIsProcessing(true);
      setHasSubmitted(true);

      try {
        const reason =
          selectedReason || additionalComments || "User provided feedback";

        let successMessage = "";

        if (modalType === "campaign") {
          // User chose "Close now but show in future"
          await setClosurePreference({
            userId,
            companyId,
            campaignId,
            wantsToSee: true,
            reason,
            isGlobalResponse: true,
            preferenceDate: effectiveDate.toISOString().split("T")[0],
          }).unwrap();

          recordClosure(
            campaignId,
            userId,
            companyId,
            closureCount,
            "TEMPORARY_CLOSE_SESSION"
          );

          successMessage =
            "The banner has been closed for now. We’ll show you relevant banners based on your choice in the future.";
        } else {
          // User chose "Stop showing this ad" (global opt-out)
          await setClosurePreference({
            userId,
            companyId,
            campaignId,
            wantsToSee: false,
            reason,
            isGlobalResponse: true,
            preferenceDate: effectiveDate.toISOString().split("T")[0],
          }).unwrap();

          recordClosure(
            campaignId,
            userId,
            companyId,
            closureCount,
            "GLOBAL_OPT_OUT"
          );

          successMessage = "We won’t show you any banners in the future.";
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

        // Even on error, close banner and modals with appropriate message
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setSharedModalOpen(false);
        handleClose();

        const errorMessage =
          modalType === "global"
            ? "We won’t show you any banners in the future."
            : "The banner has been closed for now. We’ll show you relevant banners based on your choice in the future.";

        setTimeout(() => {
          showGlobalSuccessPopup(errorMessage);
        }, 150);
      } finally {
        setIsProcessing(false);
        setTimeout(() => {
          setHasSubmitted(false);
        }, 500);
      }
    },
    [
      hasSubmitted,
      isProcessing,
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

  const handleSharedModalOnSubmit = useCallback(async () => {}, [
    handleSharedModalSubmit,
  ]);

  const handleSharedModalClose = useCallback(() => {
    setSharedModalOpen(false);
    setHasSubmitted(false);
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
          We’ve noticed you’re not interested in other products. Would you like to stop seeing them?
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
              Don’t show me any banners
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
