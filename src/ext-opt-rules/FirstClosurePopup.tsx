import React, { useState, useCallback } from "react";
import USBModal, {
  ModalHeader,
  ModalBody,
  ModalFooter,
} from "@usb-shield/react-modal";
import Button from "@usb-shield/react-button";
import { businessOptions } from "./BannerselectOptions";
import PopupCloseandShowBanner from "./PopupCloseandShow";
import SharedModal from "../../../../../packages/shared/src/utils/SharedBannerContent"

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

const FirstClosurePopup: React.FC<FirstClosurePopupProps> = ({
  isOpen,
  handleClose,
  campaignId,
  userId,
  companyId,
  closureCount = 1,
  onPreferenceComplete,
}) => {
  const [isSuccessPopupOpen, setSuccessPopupOpen] = useState(false);
  const [isDontShowAgainPopupOpen, setDontShowAgainPopupOpen] = useState(false);
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
        wantsToSee: true,       
        reason: "User chose to close temporarily",
        isGlobalResponse: false,   
        preferenceDate: effectiveDate.toISOString().split('T')[0]
      }).unwrap();

      // Record closure and close banner IMMEDIATELY
      recordClosure(campaignId, userId, companyId, closureCount, 'TEMPORARY_CLOSE_SESSION');
      
      // Close the banner first
      if (onPreferenceComplete) {
        onPreferenceComplete();
      }
      
      // Close this modal
      handleClose();
      
      // Then show success popup
      setSuccessMessage("The banner has been closed for now and will be shown to you in a future session.");
      setSuccessPopupOpen(true);
    } catch (error) {
      console.error('Error setting preference:', error);
      // Even on error, close the banner
      if (onPreferenceComplete) {
        onPreferenceComplete();
      }
      handleClose();
      setSuccessMessage("The banner has been closed for now.");
      setSuccessPopupOpen(true);
    } finally {
      setIsProcessing(false);
    }
  }, [campaignId, userId, companyId, closureCount, setClosurePreference, recordClosure, onPreferenceComplete, handleClose]);

  const handleDontShowAgainClick = useCallback(() => {
    setDontShowAgainPopupOpen(true);
  }, []);

  const handleDontShowAgainOnSubmit = useCallback(async () => {
    setDontShowAgainPopupOpen(false);
    
    // Close banner and modal immediately
    if (onPreferenceComplete) {
      onPreferenceComplete();
    }
    handleClose();
    
    // Show success popup
    setSuccessMessage("We won't show you this banner again.");
    setSuccessPopupOpen(true);
  }, [onPreferenceComplete, handleClose]);

  const handleDontShowAgainSubmit = useCallback(
    async (selectedReason: string | null, additionalComments: string) => {
      if (!campaignId || !userId || !companyId) {
        // Close banner and modal even without API call
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setDontShowAgainPopupOpen(false);
        handleClose();
        setSuccessMessage("We won't show you this banner again.");
        setSuccessPopupOpen(true);
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
          preferenceDate: effectiveDate.toISOString().split('T')[0]
        }).unwrap();

        recordClosure(
          campaignId,
          userId,
          companyId,
          closureCount,
          "PERMANENT_BLOCK"
        );

        // Close banner and modal IMMEDIATELY after API success
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setDontShowAgainPopupOpen(false);
        handleClose();
        
        // Show success popup
        setSuccessMessage("We won't show you this banner again.");
        setSuccessPopupOpen(true);
      } catch (error) {
        console.error("Error setting preference:", error);
        
        // Even on error, close banner and modal
        if (onPreferenceComplete) {
          onPreferenceComplete();
        }
        setDontShowAgainPopupOpen(false);
        handleClose();
        setSuccessMessage("We won't show you this banner again.");
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
      setClosurePreference,
      recordClosure,
      onPreferenceComplete,
      handleClose,
    ]
  );

  const handleSuccessPopupClose = useCallback(() => {
    setSuccessPopupOpen(false);
  }, []);

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

      {/* Success confirmation popup - Shows AFTER banner is closed */}
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