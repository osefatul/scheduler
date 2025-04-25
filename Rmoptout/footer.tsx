import { useEffect, useState } from "react";
import {
  FooterContainer,
  LeftText,
  RightActions,
  RMPageFooter,
} from "./RMtabfooter.styled";
import USBButton from "@usb-shield/react-button";
import RMUnEnroll from "../RMPopup.tsx/RMUnEnrollPopup";
import { useUpdateenrollUsersMutation } from "@/internal/services/rmcampaignUsersAPI";
import { Spinner } from "../../spinner";
import { useAppDispatch, useAppSelector } from "../../store/hooks";
import { clearSelectedUsers } from "../../store/userSelectionSlice";

interface RMtabfooterProps {
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
  actionType: "unenroll" | "enroll";
  rowSelection: object;
  setRowSelection: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  setShowNotification: React.Dispatch<React.SetStateAction<boolean>>;
  onResponseNotification: (
    bool: boolean,
    action?: string,
    count?: number
  ) => void;
  onUndoSelections?: () => void; // Optional callback for undo selections
}

const RMtabfooter: React.FC<RMtabfooterProps> = ({
  rowSelection = {},
  setShowNotification,
  usersData,
  actionType,
  setRowSelection,
  onResponseNotification,
  onUndoSelections,
}) => {
  const [isAboveFooter, setIsAboveFooter] = useState(false);
  const [modalIsOpen, setModalIsOpen] = useState(false);
  const [updateEnrollUsers, { isLoading: isEnrollLoading }] = useUpdateenrollUsersMutation();
  const [isProcessing, setIsProcessing] = useState(false);
  
  // Redux
  const dispatch = useAppDispatch();
  const selectedUsers = useAppSelector(state => state.userSelection.selectedUsers);
  
  const selectedRowsCount = selectedUsers.length;

  useEffect(() => {
    const handleScroll = () => {
      const scrollHeight = document.documentElement.scrollHeight;
      const scrollTop = window.scrollY;
      const clientHeight = window.innerHeight;

      if (scrollHeight - (scrollTop + clientHeight) <= 50) {
        setIsAboveFooter(true);
      } else {
        setIsAboveFooter(false);
      }
    };

    window.addEventListener("scroll", handleScroll);
    return () => {
      window.removeEventListener("scroll", handleScroll);
    };
  }, []);

  const handleEnrollUsers = async () => {
    if (selectedRowsCount === 0) {
      setShowNotification(true);
      return;
    }

    if (isProcessing) return;
    setIsProcessing(true);
    setShowNotification(false);

    try {
      const enrollData = {
        campaignId: usersData.campaignId,
        usersList: [...selectedUsers], // Use users from Redux
      };

      console.log("Sending enrollment data:", JSON.stringify(enrollData));
      console.log("Number of users being enrolled:", enrollData.usersList.length);

      const response = await updateEnrollUsers(enrollData).unwrap();
      console.log("Enroll response:", response);
      localStorage.setItem(
        "enrollCount",
        enrollData.usersList.length.toString()
      );

      // Reset selection after successful enrollment
      setRowSelection({});
      dispatch(clearSelectedUsers());
      onResponseNotification(true, "enroll", enrollData.usersList.length);
    } catch (error) {
      console.error("Error enrolling users:", error);
      onResponseNotification(true, "enroll", 0);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleUnenrollClick = () => {
    if (selectedRowsCount === 0) {
      setShowNotification(true);
      return;
    }

    setShowNotification(false);
    
    // Just open the modal - the modal will get users from Redux
    console.log("Opening modal with selected users count:", selectedRowsCount);
    setModalIsOpen(true);
  };

  const handleUndoSelections = () => {
    if (onUndoSelections) {
      onUndoSelections(); // Use the callback if provided
    } else {
      // Default behavior
      setRowSelection({});
      dispatch(clearSelectedUsers());
    }
    setShowNotification(false);
  };

  return (
    <>
      {isEnrollLoading && <Spinner />}
      <RMPageFooter className={isAboveFooter ? "above-footer" : ""}>
        <FooterContainer>
          <LeftText>Selected {selectedRowsCount} users</LeftText>

          <RightActions>
            <p onClick={handleUndoSelections}>Undo selections</p>
            <USBButton
              handleClick={() => {
                if (actionType === "unenroll") {
                  handleUnenrollClick();
                } else if (actionType === "enroll") {
                  handleEnrollUsers();
                }
              }}
            >
              {actionType === "unenroll"
                ? "Unenroll"
                : isProcessing
                ? "Processing..."
                : "Enroll"}
            </USBButton>
          </RightActions>
        </FooterContainer>
      </RMPageFooter>

      <RMUnEnroll
        modalIsOpen={modalIsOpen}
        setShowNotification={setShowNotification}
        setModalIsOpen={setModalIsOpen}
        usersData={{
          campaignId: usersData.campaignId,
          usersList: selectedUsers // Use users from Redux
        }}
        setRowSelection={setRowSelection}
        onResponseNotification={onResponseNotification}
      />
    </>
  );
};

export default RMtabfooter;