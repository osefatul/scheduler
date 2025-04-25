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
}

const RMtabfooter: React.FC<RMtabfooterProps> = ({
  rowSelection = {},
  setShowNotification,
  usersData,
  actionType,
  setRowSelection,
  onResponseNotification,
}) => {
  const [isAboveFooter, setIsAboveFooter] = useState(false);
  const [modalIsOpen, setModalIsOpen] = useState(false);
  const [updateEnrollUsers, { isLoading: isEnrollLoading }] =
    useUpdateenrollUsersMutation();
  const [isProcessing, setIsProcessing] = useState(false);
  
  // State to track selected users for both enroll and unenroll operations
  const [selectedUsersForModal, setSelectedUsersForModal] = useState<any[]>([]);
  
  const selectedRowsCount = Object.keys(rowSelection).length;

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

  // This function will be called by the table component to update our selected users
  // We're adding this explicitly to capture the selected users when the selection changes
  const updateSelectedUsers = (selectedUsersList: any[]) => {
    setSelectedUsersForModal(selectedUsersList);
    console.log("Selected users updated in RMtabfooter:", selectedUsersList);
  };

  const handleEnrollUsers = async () => {
    if (selectedRowsCount === 0) {
      setShowNotification(true);
      return;
    }

    if (isProcessing) return;
    setIsProcessing(true);
    setShowNotification(false);

    if (selectedUsersForModal.length === 0) {
      console.error("No users selected for enrollment");
      setIsProcessing(false);
      return;
    }

    try {
      const enrollData = {
        campaignId: usersData.campaignId,
        usersList: [...selectedUsersForModal], // Use the captured selected users
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
      setSelectedUsersForModal([]);
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
    
    // We're opening the modal with the already captured list of selected users
    console.log("Opening modal with selected users:", selectedUsersForModal);
    console.log("Number of users for unenrollment:", selectedUsersForModal.length);
    
    // Open the modal
    setModalIsOpen(true);
  };

  const handleUndoSelections = () => {
    setRowSelection({});
    setSelectedUsersForModal([]);
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
          usersList: selectedUsersForModal // Pass the captured selected users
        }}
        setRowSelection={setRowSelection}
        onResponseNotification={onResponseNotification}
      />
    </>
  );
};

export default RMtabfooter;