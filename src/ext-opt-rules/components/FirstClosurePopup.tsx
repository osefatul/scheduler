
import React, { useState, useCallback, useEffect } from "react";
import USBModal, {
  ModalHeader,
  ModalBody,
  ModalFooter,
} from "@usb-shield/react-modal";
import Button from "@usb-shield/react-button";
import { businessOptions } from "./BannerselectOptions";
import PopupCloseandShowBanner from "./PopupCloseandShow";
import SharedModal from "../../../../../packages/shared/src/utils/SharedBannerContent";

import { ExternalBannerPopup } from "./bannerpopup.styles";
import { useSetClosurePreferenceMutation } from "@/external/services/campaignClosureAPI";
import { useSessionClosureManager } from "@/external/utils/sessionClosureManager";
import { useLocation } from "react-router-dom";

interface FirstClosurePopupProps {
  isOpen: boolean;
  handleClose: () => void;
  campaignId?: string;
  userId?: string;
  companyId?: string;
  closureCount?: number;
  onPreferenceComplete?: () => void;
}

declare global {
  interface Window {
    globalSuccessPopupState: {
      isOpen: boolean;
      message: string;
      onClose: () => void;
    };
  }
}

export const GlobalSuccessPopup: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    const checkGlobalState = () => {
      if (typeof window !== "undefined" && window.globalSuccessPopupState) {
        if (window.globalSuccessPopupState.isOpen !== isOpen) {
          setIsOpen(window.globalSuccessPopupState.isOpen);
          setMessage(window.globalSuccessPopupState.message);
        }
      }
    };

    checkGlobalState();

    const interval = setInterval(checkGlobalState, 100);

    const handleStateChange = () => {
      checkGlobalState();
    };

    window.addEventListener(
      "globalSuccessPopupStateChanged",
      handleStateChange
    );

    return () => {
      clearInterval(interval);
      window.removeEventListener(
        "globalSuccessPopupStateChanged",
        handleStateChange
      );
    };
  }, [isOpen]);

  const handleClose = () => {
    setIsOpen(false);
    if (typeof window !== "undefined" && window.globalSuccessPopupState) {
      window.globalSuccessPopupState.isOpen = false;
      window.globalSuccessPopupState.onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <PopupCloseandShowBanner
      isOpen={isOpen}
      handleClose={handleClose}
      message={message}
    />
  );
};

const FirstClosurePopup: React.FC<FirstClosurePopupProps> = ({
  isOpen,
  handleClose,
  campaignId,
  userId,
  companyId,
  closureCount = 1,
  onPreferenceComplete,
}) => {
  const [isDontShowAgainPopupOpen, setDontShowAgainPopupOpen] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

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
    if (typeof window !== "undefined") {
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

      window.dispatchEvent(new CustomEvent("globalSuccessPopupStateChanged"));
    }
  }, []);

  const handleCloseAndShowLater = useCallback(async () => {
    if (!campaignId || !userId || !companyId) {
      console.error("Missing required parameters for preference API");
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
        preferenceDate: effectiveDate.toISOString().split("T")[0],
      }).unwrap();

      recordClosure(
        campaignId,
        userId,
        companyId,
        closureCount,
        "TEMPORARY_CLOSE_SESSION"
      );

      if (onPreferenceComplete) {
        onPreferenceComplete();
      }

      handleClose();

      setTimeout(() => {
        showGlobalSuccessPopup(
          "The banner has been closed for now and will be shown to you in a future session."
        );
      }, 150);
    } catch (error) {
      console.error("Error setting preference:", error);
      if (onPreferenceComplete) {
        onPreferenceComplete();
      }
      handleClose();

      setTimeout(() => {
        showGlobalSuccessPopup("The banner has been closed for now.");
      }, 150);
    } finally {
      setIsProcessing(false);
    }
  }, [
    campaignId,
    userId,
    companyId,
    closureCount,
    setClosurePreference,
    recordClosure,
    onPreferenceComplete,
    handleClose,
    showGlobalSuccessPopup,
  ]);

  const handleDontShowAgainClick = useCallback(() => {
    setDontShowAgainPopupOpen(true);
  }, []);

  const handleDontShowAgainOnSubmit = useCallback(async () => {
    setDontShowAgainPopupOpen(false);

    if (onPreferenceComplete) {
      onPreferenceComplete();
    }
    handleClose();

    setTimeout(() => {
      showGlobalSuccessPopup(
        "The banner has been closed for now and won’t be shown to you in the future."
      );
    }, 150);
  }, [onPreferenceComplete, handleClose, showGlobalSuccessPopup]);

  const handleDontShowAgainSubmit = useCallback(
    async (selectedReason: string | null, additionalComments: string) => {
      if (!campaignId || !userId || !companyId) {
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setDontShowAgainPopupOpen(false);
        handleClose();

        setTimeout(() => {
          showGlobalSuccessPopup(
            "The banner has been closed for now and won’t be shown to you in the future."
          );
        }, 150);
        return;
      }

      setIsProcessing(true);

      try {
        const reason =
          selectedReason ||
          additionalComments ||
          "User chose not to see campaign again";

        await setClosurePreference({
          userId,
          companyId,
          campaignId,
          wantsToSee: false,
          reason,
          isGlobalResponse: false,
          preferenceDate: effectiveDate.toISOString().split("T")[0],
        }).unwrap();

        recordClosure(
          campaignId,
          userId,
          companyId,
          closureCount,
          "PERMANENT_BLOCK"
        );

        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setDontShowAgainPopupOpen(false);
        handleClose();

        setTimeout(() => {
          showGlobalSuccessPopup(
            "The banner has been closed for now and won’t be shown to you in the future."
          );
        }, 150);
      } catch (error) {
        console.error("Error setting preference:", error);

        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setDontShowAgainPopupOpen(false);
        handleClose();

        setTimeout(() => {
          showGlobalSuccessPopup(
            "The banner has been closed for now and won’t be shown to you in the future."
          );
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
      setClosurePreference,
      recordClosure,
      onPreferenceComplete,
      handleClose,
      showGlobalSuccessPopup,
    ]
  );

  const handleDontShowAgainPopupClose = useCallback(() => {
    setDontShowAgainPopupOpen(false);
  }, []);

  return (
    <>
      <ExternalBannerPopup>
        <USBModal
          handleClose={handleClose}
          id="first-closure-modal"
          isOpen={isOpen}
        >
          <ModalHeader id="first-closure-header">Close banner?</ModalHeader>
          <ModalBody>
            The banner will be closed for now but you can choose to see it
            later.
          </ModalBody>
          <ModalFooter>
            <Button
              ctaStyle="standard"
              emphasis="heavy"
              handleClick={handleCloseAndShowLater}
              disabled={isProcessing}
            >
              {isProcessing ? "Processing..." : "Close & show later"}
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

