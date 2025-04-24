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
  rowSelection: Record<string, boolean>;
  setRowSelection: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  setShowNotification: React.Dispatch<React.SetStateAction<boolean>>;
  onHideNotification: (bool: boolean) => void;
}

const RMtabfooter: React.FC<RMtabfooterProps> = ({
  rowSelection = {},
  setShowNotification,
  usersData,
  actionType,
  setRowSelection,
  onHideNotification,
}) => {
  const [isAboveFooter, setIsAboveFooter] = useState(false);
  const [modalIsOpen, setModalIsOpen] = useState(false);
  const [updateEnrollUsers, { isLoading: isEnrollLoading }] = useUpdateenrollUsersMutation();
  const [isProcessing, setIsProcessing] = useState(false);
  
  const selectedRowsCount = Object.keys(rowSelection).length;
  
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
    if (selectedRowsCount === 0) {
      setShowNotification(true);
      return;
    }
    
    // Prevent multiple submissions
    if (isProcessing) return;
    setIsProcessing(true);
    
    setShowNotification(false);
    
    // Get the selected rows directly from the rowSelection object and usersData
    const selectedRows = Object.keys(rowSelection).map(rowId => {
      const index = parseInt(rowId);
      return usersData.usersList[index];
    }).filter(Boolean); // Filter out any undefined values
    
    if (selectedRows.length === 0) {
      console.error("No users selected for enrollment");
      setIsProcessing(false);
      return;
    }
    
    try {
      // Create a deep copy of the request to avoid reference issues
      const enrollData = {
        campaignId: usersData.campaignId,
        usersList: [...selectedRows] // Explicitly create a new array with the selected users
      };
      
      console.log("Sending enrollment data:", JSON.stringify(enrollData));
      
      const response = await updateEnrollUsers(enrollData).unwrap();
      console.log("Enroll response:", response);
      
      // Reset selection after successful enrollment
      setRowSelection({});
      onHideNotification(true);
    } catch (error) {
      console.error("Error enrolling users:", error);
      onHideNotification(false);
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
    
    // Get the selected users before opening the modal
    const selectedUsers = Object.keys(rowSelection).map(rowId => {
      const index = parseInt(rowId);
      return usersData.usersList[index];
    }).filter(Boolean);
    
    // Open the modal with selected users
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
              disabled={selectedRowsCount === 0 || isProcessing || isEnrollLoading}
            >
              {actionType === "unenroll" ? "Unenroll" : isProcessing ? "Processing..." : "Enroll"}
            </USBButton>
          </RightActions>
        </FooterContainer>
      </RMPageFooter>

      <RMUnEnroll
        modalIsOpen={modalIsOpen}
        setShowNotification={setShowNotification}
        setModalIsOpen={setModalIsOpen}
        usersData={{
          ...usersData,
          usersList: Object.keys(rowSelection).map(rowId => {
            const index = parseInt(rowId);
            return usersData.usersList[index];
          }).filter(Boolean) // Make sure we only pass selected users to the modal
        }}
        setRowSelection={setRowSelection}
        onHideNotification={onHideNotification}
      />
    </>
  );
};

export default RMtabfooter;