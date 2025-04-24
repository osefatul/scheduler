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
    
    setShowNotification(false);
    
    // Trigger the button click programmatically to get selected users
    const button = document.getElementById(
      "button-primary-button-test-id-enroll_usb-table_default--id"
    );

    if (button) {
      button.click();
    }
    
    const selectedRows = Object.keys(rowSelection).map(
      (rowId) => usersData.usersList[parseInt(rowId)]
    );
    
    if (selectedRows.length === 0) {
      console.error("No users selected");
      return;
    }
    
    try {
      const enrollData = {
        ...usersData,
        usersList: selectedRows,
      };
      
      const response = await updateEnrollUsers(enrollData).unwrap();
      console.log("Enroll response:", response);
      
      // Reset selection after successful enrollment
      setRowSelection({});
      onHideNotification(true);
    } catch (error) {
      console.error("Error enrolling users:", error);
      onHideNotification(false);
    }
  };

  const handleUnenrollClick = () => {
    if (selectedRowsCount === 0) {
      setShowNotification(true);
      return;
    }
    
    setShowNotification(false);
    
    // Trigger the button click programmatically
    const button = document.getElementById(
      "button-primary-button-test-id_usb-table_default--id"
    );
    
    if (button) {
      button.click();
    }
    
    // Open the modal for unenroll action
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
              disabled={selectedRowsCount === 0}
            >
              {actionType === "unenroll" ? "Unenroll" : "Enroll"}
            </USBButton>
          </RightActions>
        </FooterContainer>
      </RMPageFooter>

      <RMUnEnroll
        modalIsOpen={modalIsOpen}
        setShowNotification={setShowNotification}
        setModalIsOpen={setModalIsOpen}
        usersData={usersData}
        setRowSelection={setRowSelection}
        onHideNotification={onHideNotification}
      />
    </>
  );
};

export default RMtabfooter;