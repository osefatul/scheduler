import USBButton from "@usb-shield/react-button";
import RMUnEnroll from "../RMPopup.tsx/RMUnEnrollPopup";
import { useUpdateEnrollUsersMutation } from "@/internal/services/rmCampaignUsersAPI";
import { useEffect, useState } from "react";
import { Spinner } from "../common/Spinner";
import { RMPageFooter, FooterContainer, LeftText, RightActions } from "./RMTabFooter.styles";

interface RMtabFooterProps {
  userData: {
    campaignId: string;
    unEnrollReason: string;
    additionalComments: string;
    usersList: {
      userName: string;
      emailId: string;
      companyName: string;
      name: string;
      usExternalId: string;
      telephoneNumber: string;
    }[];
  };
  actionType: "unenroll" | "enroll";
  rowSelection: object;
  setRowSelection: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  setShowNotification: React.Dispatch<React.SetStateAction<boolean>>;
  onHideNotification: (bool:boolean) => void; // Callback function to hide the notification
}

const RMtabFooter: React.FC<RMtabFooterProps> = ({
  rowSelection = {},
  setShowNotification,
  userData,
  actionType,
  setRowSelection,
  onHideNotification,
}) => {
  const [isAboveFooter, setIsAboveFooter] = useState(false);
  const [modalIsOpen, setModalIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  
  useEffect(() => {
    const handleScroll = () => {
      const scrollHeight = document.documentElement.scrollHeight;
      const scrollTop = window.scrollY;
      const clientHeight = window.innerHeight;
      
      // Check if the user has scrolled near the bottom of the page
      if ((scrollHeight - (scrollTop + clientHeight)) <= 50) {
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
  
  const [updateEnrollUsers] = useUpdateEnrollUsersMutation();
  
  const handleEnrollUsers = async () => {
    if (Object.keys(rowSelection).length === 0) {
      setShowNotification(true);
      return;
    }
    
    setIsLoading(true);
    
    try {
      // Get selected rows and prepare data for the API
      const selectedRowIds = Object.keys(rowSelection);
      const selectedUsers = selectedRowIds.map(id => {
        // Find the corresponding data from the userData
        // This assumes that rows in rowSelection match indices in userData.usersList
        return userData.usersList[parseInt(id)];
      });
      
      // Create a properly formatted payload
      const payload = {
        ...userData,
        usersList: selectedUsers
      };
      
      // Call the API with the prepared payload
      const response = await updateEnrollUsers(payload).unwrap();
      console.log("Enrollment successful:", response);
      
      // Clear selections after successful operation
      setRowSelection({});
      onHideNotification(false);
    } catch (error) {
      console.error("Error during enrollment:", error);
      onHideNotification(true);
    } finally {
      setIsLoading(false);
    }
  };
  
  const handleUnenrollClick = () => {
    if (Object.keys(rowSelection).length === 0) {
      setShowNotification(true);
      return;
    }
    
    setModalIsOpen(true);
    setShowNotification(false);
  };
  
  const handleUndoSelections = () => {
    setRowSelection({});
    setShowNotification(false);
  };
  
  return (
    <>
      <RMPageFooter className={isAboveFooter ? "above-footer" : ""}>
        <FooterContainer>
          <LeftText>Selected {Object.keys(rowSelection).length} users</LeftText>
          
          <RightActions>
            <p onClick={handleUndoSelections}>Undo selections</p>
            <USBButton
              variant="primary"
              size="medium"
              handleClick={() => {
                if (actionType === "unenroll") {
                  handleUnenrollClick();
                } else if (actionType === "enroll") {
                  handleEnrollUsers();
                }
              }}
              disabled={Object.keys(rowSelection).length === 0 || isLoading}
            >
              {isLoading ? <Spinner /> : actionType === "unenroll" ? "Unenroll" : "Enroll"}
            </USBButton>
          </RightActions>
        </FooterContainer>
      </RMPageFooter>
      
      <RMUnEnroll
        modalIsOpen={modalIsOpen}
        setShowNotification={setShowNotification}
        setModalIsOpen={setModalIsOpen}
        userData={userData}
        setRowSelection={setRowSelection}
        onHideNotification={onHideNotification}
      />
    </>
  );
};

export default RMtabFooter;