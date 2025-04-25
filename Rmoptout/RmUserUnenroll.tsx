import USBTable from "@usb-shield/react-table";
import { useState, useMemo, useEffect } from "react";
import { getCoreRowModel } from "@tanstack/react-table";
import { useGetCampaignUnEnrollUsersByIdQuery } from "@/internal/services/rmcampaignUsersAPI";
import RMtabfooter from "../RMTabFooter/RMtabfooter";
import { RMPagination } from "../RMoptoutcampaignList/RMoptoutcampaignList.styled";
import USBPagination from "@usb-shield/react-pagination";
import USBSearchInput from "@usb-shield/react-search-input";
import { RMsearch } from "../RMSearchUsers/RMSearchUsers.styles";
import {
  BannerPreview,
  NoBannerWrapper,
  NoRMCampainWrapper,
  StyledParagraph,
} from "../RMOptflowCommon.styled";
import { RMStyledTableWrapper } from "../RMEnrollUsersTable/RMEnrollUserstable.styles";
import { USBIconInfo, USBIconSort } from "@usb-shield/react-icons";
import { StyledParagraphCenter } from "../../ManageCampaign/ManageCampaign.styled";
import USBButton from "@usb-shield/react-button";
import { Spinner } from "../../spinner";

interface ControlOptions {
  controlled: boolean;
  handleForwardClick: () => void;
  handleBackwardClick: () => void;
}

export const RMUsersUnEnrollTable = ({
  campaignId,
  onResponseNotification,
  setShowNotification
}: {
  campaignId: string;
  onResponseNotification: (bool: boolean,action?:string,count?:number) => void;
  setShowNotification: React.Dispatch<React.SetStateAction<boolean>>;
}) => {
  // Only call the query if campaignId is not empty
  const { data: usersData, isLoading: isDataLoading } = useGetCampaignUnEnrollUsersByIdQuery(campaignId, {
    skip: !campaignId,
  });
  
  const [sortConfig, setSortConfig] = useState<{
    key: string;
    direction: "asc" | "desc";
  } | null>(null);
  
  const [searchInputValue, setSearchInputValue] = useState("");
  const [filteredData, setFilteredData] = useState<any>([]);
  const [_data, setData] = useState<any>([]);
  const [test, setTest] = useState<any[]>([]);
  const [isdata, setIsdata] = useState(false);
  const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});
  const [{ pageIndex, pageSize }, setPagination] = useState({
    pageIndex: 0,
    pageSize: 10,
  });
  
  // Track selected users across all pages
  const [selectedUsers, setSelectedUsers] = useState<any[]>([]);

  const [enroll, setEnroll] = useState<{
    campaignId: string;
    usersList: any[];
  }>({
    campaignId: campaignId,
    usersList: [], // Initialize with empty array
  });

  // Initialize filteredData when usersData is loaded
  useEffect(() => {
    if (usersData?.usersList) {
      setFilteredData(usersData.usersList);
    }
  }, [usersData]);

  // Update enroll state when campaignId changes
  useEffect(() => {
    setEnroll(prev => ({
      ...prev,
      campaignId: campaignId
    }));
  }, [campaignId]);

  const handleSort = (key: string) => {
    setSortConfig((prev) => {
      if (prev?.key === key) {
        return {
          key,
          direction: prev.direction === "asc" ? "desc" : "asc",
        };
      }
      return { key, direction: "asc" };
    });
  };

  const columns = useMemo(
    () => [
      {
        header: (
          <div className="theader" onClick={() => handleSort("userName")}>
            Username <USBIconSort />
          </div>
        ),
        accessorKey: "userName",
      },
      {
        header: (
          <div className="theader" onClick={() => handleSort("companyName")}>
            Company name <USBIconSort />
          </div>
        ),
        accessorKey: "companyName",
      },
      {
        header: (
          <div className="theader" onClick={() => handleSort("emailId")}>
            Email ID <USBIconSort />
          </div>
        ),
        accessorKey: "emailId",
      },
      {
        header: (
          <div className="theader" onClick={() => handleSort("name")}>
            Name <USBIconSort />
          </div>
        ),
        accessorKey: "name",
      },
      {
        header: (
          <div className="theader" onClick={() => handleSort("reason")}>
            Reason <USBIconSort />
          </div>
        ),
        accessorKey: "unEnrollReason",
      },
    ],
    [sortConfig]
  );

  const tableData = useMemo(() => {
    let data = filteredData.length > 0 ? filteredData : usersData?.usersList || [];
    
    if (sortConfig) {
      data = [...data].sort((a, b) => {
        const aValue = a[sortConfig.key] || '';
        const bValue = b[sortConfig.key] || '';
        
        if (aValue < bValue) {
          return sortConfig.direction === "asc" ? -1 : 1;
        }
        if (aValue > bValue) {
          return sortConfig.direction === "asc" ? 1 : -1;
        }
        return 0;
      });
    }
    
    return data;
  }, [usersData, filteredData, sortConfig]);

  // Calculate the data for the current page
  const currentPageData = useMemo(() => {
    const startIndex = pageIndex * pageSize;
    return tableData.slice(startIndex, startIndex + pageSize);
  }, [tableData, pageIndex, pageSize]);

  // Update _data when tableData or pagination changes
  useEffect(() => {
    setData(currentPageData);
  }, [currentPageData]);

  // Handle checkbox selection
  const handleRowSelectionChange = (newSelection: any) => {
    // Check if newSelection is a function and call it with the current state
    const updatedSelection =
      typeof newSelection === "function" ? newSelection(rowSelection) : newSelection;
  
    console.log("Row selection changed:", updatedSelection); 
    console.log("Type of updatedSelection:", typeof updatedSelection);
    console.log("Keys in updatedSelection:", Object.keys(updatedSelection || {}));
  
    // Update the rowSelection state
    setRowSelection(updatedSelection);
  
    // If any checkbox is selected, hide the error message
    if (Object.keys(updatedSelection).length > 0) {
      setShowNotification(false);
    }
    
    // CRITICAL FIX: Map selections to actual users using the current page data
    const newSelectedUsers = Object.keys(updatedSelection)
      .filter(key => updatedSelection[key]) // Only include selected rows
      .map(rowId => {
        const index = parseInt(rowId);
        // Use currentPageData to get the actual user object
        if (index >= 0 && index < currentPageData.length) {
          return currentPageData[index];
        }
        return null;
      })
      .filter(Boolean);
    
    console.log("New selected users from current page:", newSelectedUsers);
    
    // Update the complete list of selected users
    const updatedUsers = [...selectedUsers];
    
    // Add newly selected users
    newSelectedUsers.forEach(user => {
      if (!updatedUsers.some(u => u.userName === user.userName)) {
        updatedUsers.push(user);
      }
    });
    
    // Remove deselected users from the current page
    const currentPageUsernames = currentPageData.map(user => user.userName);
    const selectedUsernames = Object.keys(updatedSelection)
      .filter(key => updatedSelection[key])
      .map(rowId => {
        const index = parseInt(rowId);
        return index >= 0 && index < currentPageData.length ? currentPageData[index].userName : null;
      })
      .filter(Boolean);
    
    const finalUsers = updatedUsers.filter(user => {
      // Keep users not on current page
      if (!currentPageUsernames.includes(user.userName)) {
        return true;
      }
      // For users on current page, only keep if still selected
      return selectedUsernames.includes(user.userName);
    });
    
    setSelectedUsers(finalUsers);
    
    // Update the enroll state with the selected users
    setEnroll(prev => ({
      ...prev,
      usersList: finalUsers
    }));
    
    console.log("Updated full selected users list:", finalUsers);
  };

  // Handle page change
  const handlePageChange = (newPage: number) => {
    // Change page
    setPagination(prev => ({
      ...prev,
      pageIndex: newPage - 1,
    }));
    
    // Reset the current page's row selection state (visual checkboxes)
    // but maintain the overall selected users list
    setRowSelection({});
  };

  const optionsV8 = {
    data: currentPageData,
    setData,
    columns,
    state: {
      rowSelection,
      pagination: { pageIndex, pageSize },
    },
    onPaginationChange: setPagination,
    manualPagination: true,
    pageCount: Math.ceil(tableData.length / pageSize),
    getCoreRowModel: getCoreRowModel(),
    onRowSelectionChange: handleRowSelectionChange,
    enableRowSelection: true,
    enableMultiRowSelection: true,
  };

  const controlOptions: ControlOptions = {
    controlled: true,
    handleForwardClick: () => {
      // Save current page selections before going forward
      // Move to next page
      setPagination((prev) => ({
        ...prev,
        pageIndex: prev.pageIndex + 1
      }));
      
      // Clear row selection for the new page (visual only)
      setRowSelection({});
    },
    handleBackwardClick: () => {
      // Save current page selections before going backward
      // Move to previous page
      setPagination((prev) => ({
        ...prev,
        pageIndex: Math.max(prev.pageIndex - 1, 0),
      }));
      
      // Clear row selection for the new page (visual only)
      setRowSelection({});
    },
  };

  // Update the handleSearchButtonClick function to match the expected type
  const handleSearchButtonClick = () => {
    console.log("Search form submitted:", searchInputValue);
    if (!searchInputValue) {
      // If the search input is empty, reset the filtered data to show all records
      setFilteredData(usersData?.usersList || []); // Reset to all records
      setPagination((prev) => ({ ...prev, pageIndex: 0 })); // Reset to the first page
      return;
    }

    const temp = tableData.filter((item: any) => {
      return (
        item.userName?.toLowerCase().includes(searchInputValue.toLowerCase()) || // Convert both to lowercase
        item.emailId?.toLowerCase().includes(searchInputValue.toLowerCase()) || // Convert both to lowercase
        item.name?.toLowerCase().includes(searchInputValue.toLowerCase()) || // Convert both to lowercase
        item.companyName?.toLowerCase().includes(searchInputValue.toLowerCase()) // Convert both to lowercase
      );
    });
    console.log(temp, "filterdata");
    setTest(temp);

    if (temp.length === 0) {
      console.log("No matching records found");
    }
    setFilteredData(temp);
    setPagination((prev) => ({ ...prev, pageIndex: 0 })); // Reset to the first page
    setIsdata(true);
    
    // Maintain selections after search
    setRowSelection({});
  };

  // Show loading spinner while data is being fetched
  if (isDataLoading) {
    return <Spinner />;
  }

  return (
    <RMStyledTableWrapper>
      <RMsearch>
        <USBSearchInput
          type="search"
          placeholder="Search by user name or companyname"
          handleChange={(e: React.ChangeEvent<HTMLInputElement>) => {
            const value = e.target.value;
            setSearchInputValue(value);

            // If the input is cleared, reset to show all records
            if (value.length === 0) {
              setFilteredData(usersData?.usersList || []);
              setPagination((prev) => ({ ...prev, pageIndex: 0 }));
              setIsdata(false);
            }
          }}
          onKeyPress={(e: React.KeyboardEvent<HTMLInputElement>) => {
            if (e.key === "Enter") {
              handleSearchButtonClick();
            }
          }}
        />
        <USBButton
          dataTestId="manageuser-search-button"
          title="home"
          variant="primary"
          iconAssistiveText={{
            label: "Search",
          }}
          ariaLabel={
            tableData?.length > 0
              ? "Total available " + tableData.length + " search results"
              : "Search"
          }
          handleClick={handleSearchButtonClick}
        >
          Search
        </USBButton>
        <StyledParagraph>
          VIEWING {isdata && filteredData.length === 0 ? 0 : currentPageData.length} of{' '}
          {filteredData.length > 0 ? filteredData.length : 0} USERS
        </StyledParagraph>
      </RMsearch>

      {isdata && test.length === 0 ? (
        <BannerPreview>
          <NoBannerWrapper>
            <USBIconInfo size={20} />
            <StyledParagraph>
              Table data is not matching with search input data
            </StyledParagraph>
          </NoBannerWrapper>
        </BannerPreview>
      ) : tableData.length > 0 ? (
        <>
          <USBTable
            options={optionsV8}
            borders="none"
            isZebraStriped={true}
            batchActionsBar
            toolBarActions={[]}
          />

          <RMPagination>
            <USBPagination
              currentPage={filteredData.length === 0 ? 1 : pageIndex + 1}
              pageSelectionType="menu"
              totalCount={
                filteredData.length === 0
                  ? 1
                  : Math.ceil(filteredData.length / pageSize)
              }
              handlePageChange={handlePageChange}
              paginationAriaLabel={"Pagination Navigation"}
              backwardButtonAriaLabel="Previous page"
              forwardButtonAriaLabel="Next page"
              forwardIconTitle="Next page"
              backwardIconTitle="Previous page"
              addClasses="pagination-component"
              paginationLabelFrom="Page"
              paginationLabelOf="of"
              controlOptions={controlOptions}
            />
          </RMPagination>

          <RMtabfooter
            usersData={{
              campaignId: enroll.campaignId,
              usersList: selectedUsers // Pass the complete list of selected users
            }}
            actionType="enroll"
            setRowSelection={setRowSelection}
            onResponseNotification={(bool: boolean) =>
              onResponseNotification(bool, "enroll", selectedUsers.length)
            }
            rowSelection={rowSelection}
            setShowNotification={setShowNotification}
          />
        </>
      ) : (
        <NoRMCampainWrapper>
          <USBIconInfo size={20} />
          <StyledParagraphCenter>
            You don't have any user.
          </StyledParagraphCenter>
        </NoRMCampainWrapper>
      )}
    </RMStyledTableWrapper>
  );
};