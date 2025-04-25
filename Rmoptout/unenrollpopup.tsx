import React, { useRef, useState, useEffect } from "react";
import USBModal, {
  ModalHeader,
  ModalBody,
  ModalFooter,
} from "@usb-shield/react-modal";
import USBButton from "@usb-shield/react-button";
import {
  UnEnrollPopupSelect,
  UnEnrollPopupTextarea,
} from "./RMUnEnrollPopup.styles";
//select
import USBSelect from "@usb-shield/react-forms-select";
//textarea
import USBTextArea from "@usb-shield/react-forms-text-area";
import { USBFormsHelperText } from "@usb-shield/react-forms-base";
import { useUpdateUnenrollUsersMutation } from "@/internal/services/rmcampaignUsersAPI";
import { Spinner } from "../../spinner";
import { USBIconWarningSign } from '@usb-shield/react-icons';
import { useAppDispatch, useAppSelector } from "../../store/hooks";
import { clearSelectedUsers } from "../../store/userSelectionSlice";

export interface RMUnEnrolledUsersPopup {
  modalIsOpen: boolean;
  setModalIsOpen: React.Dispatch<React.SetStateAction<boolean>>;
  usersData: {
    campaignId: string;
    unEnrollReason?: string;
    additionalComments?: string;
    usersList: {
      userName: string;
      emailId: string;
      companyName: string;
      name: string;
      usbExternalId: string;
      telephoneNumber: string;
    }[];
  };
  setRowSelection?: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  onResponseNotification: (bool: boolean) => void;
  setShowNotification: React.Dispatch<React.SetStateAction<boolean>>;
}

const RMUnEnroll: React.FC<RMUnEnrolledUsersPopup> = ({
  modalIsOpen,
  setModalIsOpen,
  usersData,
  setRowSelection,
  onResponseNotification,
  setShowNotification,
}) => {
  const [selectedReason, setSelectedReason] = useState<string | null>(null);
  const [additionalComments, setAdditionalComments] = useState<string>("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [updateUnenrollUsers, { isLoading }] = useUpdateUnenrollUsersMutation();
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  // Redux
  const dispatch = useAppDispatch();
  const selectedUsers = useAppSelector(state => state.userSelection.selectedUsers);
  
  // Reset form when modal is opened
  useEffect(() => {
    if (modalIsOpen) {
      setSelectedReason(null);
      setAdditionalComments("");
      setErrorMessage(null);
      setIsSubmitting(false);
      console.log("Modal opened with users from Redux:", selectedUsers);
    }
  }, [modalIsOpen, selectedUsers]);

  const handleReasonChange = (value: any) => {
    setSelectedReason(value.inputValue);
    if (value.inputValue === "other") {
      setErrorMessage(null);
    }
    setErrorMessage(null);
  };

  const handleCommentsChange = (status: any) => {
    setAdditionalComments(status.inputValue);
    if (selectedReason === "other" && status.inputValue.trim()) {
      setErrorMessage(null);
    }
  };

  // Calculate the length of the usersList
  const usersListLength = selectedUsers.length;

  const handleProceedClick = async () => {
    // Prevent multiple submissions
    if (isSubmitting) return;
    setIsSubmitting(true);
    
    // Validate inputs
    if (!selectedReason) {
      setErrorMessage("Select at least one option.");
      setIsSubmitting(false);
      return;
    }

    if (selectedReason === "other" && !additionalComments.trim()) {
      setErrorMessage("Please provide additional comments for 'other' reason.");
      setIsSubmitting(false);
      return;
    }

    // Clear error message if validation passes
    setErrorMessage(null);
    
    if (selectedUsers.length === 0) {
      console.error("No users selected for unenrollment");
      setIsSubmitting(false);
      return;
    }

    // Create the payload for the API
    const updatedUsersData = {
      campaignId: usersData.campaignId,
      unEnrollReason: selectedReason || "",
      additionalComments: additionalComments,
      usersList: [...selectedUsers] // Use users from Redux
    };

    try {
      console.log("Sending unenroll data:", JSON.stringify(updatedUsersData));
      const response = await updateUnenrollUsers(updatedUsersData).unwrap();
      console.log("Unenroll response:", response);
    
      // Reset selection after successful unenrollment
      if (setRowSelection) {
        setRowSelection({});
      }
      
      // Clear Redux selections
      dispatch(clearSelectedUsers());
      
      localStorage.setItem("unEnrollCount", usersListLength.toString());
      onResponseNotification(true);
  
      setModalIsOpen(false);
      setShowNotification(false);
    } catch (error) {
      console.error("Error unenrolling users:", error);
      onResponseNotification(false);
    } finally {
      setIsSubmitting(false);
    }
  };

  const selectRef = useRef(null);

  const businessOptions = [
    {
      value: "Recently/previously discussed.Not intrested",
      content: "Recently/previously discussed.Not intrested"
    },
    {
      value: "Wrong time to suggest another product",
      content: "Wrong time to suggest another product"
    },
    {
      value: "Prefer to discuss with specific person at clients side instead",
      content: "Prefer to discuss with specific person at clients side instead"
    },
    {
      value: "Data quality/validity concern",
      content: "Data quality/validity concern"
    },
    { value: "other", content: "other" },
  ];

  // If we have no users, don't render the modal
  if (!modalIsOpen) return null;

  return (
    <>
      {isLoading && <Spinner />}
      <USBModal
        content={{
          closeIconAriaLabel: "close modal one",
        }}
        id="test-modal"
        isOpen={modalIsOpen}
        handleClose={() => setModalIsOpen(false)}
      >
        <ModalHeader id="mhID">Unenroll users</ModalHeader>
        <ModalBody>
          <p>
            {usersListLength} users will be unenrolled from the campaign. Provide a reason.
          </p>
          <UnEnrollPopupSelect>
            <USBSelect
              ref={selectRef}
              inputName="select-name"
              labelText="Choose a reason"
              optionPlaceholderText="Select"
              statusUpdateCallback={handleReasonChange}
              optionsArray={businessOptions}
              customStyles={{
                option: (provided: any) => ({
                  ...provided,
                  whiteSpace: "normal",
                  wordWrap: "break-word",
                }),
              }}
            />
            {errorMessage && !selectedReason && (
              <p style={{ color: "rgb(207, 42, 54)", fontSize: '13px', marginTop: "5px" }}>
                <USBIconWarningSign colorVariant="error" 
                  style={{ width: '13px', height: '13px', marginLeft: '2px' }} />
                <span style={{marginLeft:'5px'}}>{errorMessage}</span>
              </p>
            )}
          </UnEnrollPopupSelect>
          
          <UnEnrollPopupTextarea>
            <USBTextArea
              inputName="business-purpose"
              labelText="Tell us more"
              maxlength={50}
              statusUpdateCallback={handleCommentsChange}
            >
            {!(selectedReason === "other") && (
                <USBFormsHelperText labelFor="text-area">
                  Optional
                </USBFormsHelperText>
            )}
            {errorMessage && selectedReason === "other" && !additionalComments.trim() && (
                <p style={{ color: "rgb(207, 42, 54)", fontSize: '13px', marginTop: "5px" }}>
                  <USBIconWarningSign
                    colorVariant="error"
                    style={{ width: '13px', height: '13px', marginLeft: '2px' }}
                  />
                  <span style={{ marginLeft: '5px' }}>{errorMessage}</span>
                </p>
              )}
            </USBTextArea>
          </UnEnrollPopupTextarea>
        </ModalBody>
        <ModalFooter>
          <USBButton 
            id="primary-modal-button" 
            handleClick={handleProceedClick}
            disabled={isSubmitting || isLoading}
          >
            {isSubmitting ? "Processing..." : "Proceed"}
          </USBButton>
        </ModalFooter>
      </USBModal>
    </>
  );
};

export default RMUnEnroll;