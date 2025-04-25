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
  
  // Store direct references to the selected users
  const [modalUserData, setModalUserData] = useState({
    campaignId: usersData.campaignId,
    usersList: []
  });

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
    console.log("Selected rows count:", selectedRowsCount);
    if (selectedRowsCount === 0) {
      setShowNotification(true);
      return;
    }

    // Prevent multiple submissions
    if (isProcessing) return;
    setIsProcessing(true);

    setShowNotification(false);

    // Get the selected data directly from the usersData based on current selections
    const selectedUsers = Object.keys(rowSelection)
      .map(rowId => usersData.usersList[parseInt(rowId)])
      .filter(Boolean);

    if (selectedUsers.length === 0) {
      console.error("No users selected for enrollment");
      setIsProcessing(false);
      return;
    }

    try {
      const enrollData = {
        campaignId: usersData.campaignId,
        usersList: selectedUsers,
      };

      console.log("Sending enrollment data:", JSON.stringify(enrollData));

      const response = await updateEnrollUsers(enrollData).unwrap();
      console.log("Enroll response:", response);
      localStorage.setItem(
        "enrollCount",
        enrollData.usersList.length.toString()
      ); 

      // Reset selection after successful enrollment
      setRowSelection({});
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
    
    // CRITICAL FIX: Extract the selected users directly from rowSelection
    const selectedUsers = Object.keys(rowSelection)
      .map(rowId => {
        // First try to get the user from the current page data
        const user = usersData.usersList[parseInt(rowId)];
        return user;
      })
      .filter(Boolean); // Filter out any undefined values
    
    console.log("Users selected for unenrollment:", selectedUsers);
    
    // Set the modal data with the selected users
    setModalUserData({
      campaignId: usersData.campaignId,
      usersList: selectedUsers
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
          usersList: modalUserData.usersList
        }}
        setRowSelection={setRowSelection}
        onResponseNotification={onResponseNotification}
      />
    </>
  );
};

export default RMtabfooter;