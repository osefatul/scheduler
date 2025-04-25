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
  const [selectedUsers, setSelectedUsers] = useState<any[]>([]);
  const [popupUsersData, setPopupUsersData] = useState<any>({
    ...usersData,
    usersList: []
  });

  const selectedRowsCount = Object.keys(rowSelection).length;

  // Update selectedUsers when rowSelection changes
  useEffect(() => {
    if (selectedRowsCount > 0 && usersData.usersList && usersData.usersList.length > 0) {
      const selectedUsersList = Object.keys(rowSelection)
        .map((rowId) => {
          const index = parseInt(rowId);
          return usersData.usersList[index];
        })
        .filter(Boolean);
      
      setSelectedUsers(selectedUsersList);
      console.log("Selected users updated:", selectedUsersList);
    } else {
      setSelectedUsers([]);
    }
  }, [rowSelection, usersData]);

  useEffect(() => {
    const handleScroll = () => {
      const scrollHeight = document.documentElement.scrollHeight;
      const scrollTop = window.scrollY;
      const clientHeight = window.innerHeight;

      // Check if the user has scrolled near the bottom of the page
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
    console.log("Selected rows count:", selectedRowsCount);
    if (selectedRowsCount === 0) {
      setShowNotification(true);
      return;
    }

    // Prevent multiple submissions
    if (isProcessing) return;
    setIsProcessing(true);

    setShowNotification(false);

    // Use the selectedUsers state that we maintain
    if (selectedUsers.length === 0) {
      console.error("No users selected for enrollment");
      setIsProcessing(false);
      return;
    }

    try {
      const enrollData = {
        campaignId: usersData.campaignId,
        usersList: [...selectedUsers],
      };

      console.log("Sending enrollment data:", JSON.stringify(enrollData));

      const response = await updateEnrollUsers(enrollData).unwrap();
      console.log("Enroll response:", response);
      localStorage.setItem(
        "enrollCount",
        enrollData.usersList.length.toString()
      ); // Store the data in local storage

      // Reset selection after successful enrollment
      setRowSelection({});
      onResponseNotification(true, "enroll", enrollData.usersList.length); // Pass the user count
    } catch (error) {
      console.error("Error enrolling users:", error);
      onResponseNotification(true, "enroll", 0); // Pass the user count
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
    
    // Explicitly prepare the data for the popup right at the moment of opening
    const usersToUnenroll = selectedUsers.length > 0 ? selectedUsers : 
      Object.keys(rowSelection)
        .map((rowId) => {
          const index = parseInt(rowId);
          return usersData.usersList[index];
        })
        .filter(Boolean);
    
    console.log("Users to unenroll:", usersToUnenroll);
    
    // Create a fresh copy of the data for the popup
    setPopupUsersData({
      campaignId: usersData.campaignId,
      unEnrollReason: "",
      additionalComments: "",
      usersList: usersToUnenroll
    });
    
    // Open the modal
    setModalIsOpen(true);
  };

  const handleUndoSelections = () => {
    setRowSelection({});
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
              // disabled={selectedRowsCount === 0 || isProcessing || isEnrollLoading}
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
        usersData={popupUsersData}
        setRowSelection={setRowSelection}
        onResponseNotification={onResponseNotification}
      />
    </>
  );
};

export default RMtabfooter;