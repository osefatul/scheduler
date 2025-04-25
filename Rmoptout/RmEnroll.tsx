import USBTable from "@usb-shield/react-table";
import { useState, useMemo, useEffect } from "react";
import { getCoreRowModel } from "@tanstack/react-table";
import { useGetCampaignEnrollUsersByIdQuery } from "@/internal/services/rmcampaignUsersAPI";
import RMtabfooter from "../RMTabFooter/RMtabfooter";
import { RMPagination } from "../RMoptoutcampaignList/RMoptoutcampaignList.styled";
import USBPagination from "@usb-shield/react-pagination";
import USBButton from "@usb-shield/react-button";
import USBSearchInput from "@usb-shield/react-search-input";
import { RMsearch } from "../RMSearchUsers/RMSearchUsers.styles";
import {
  StyledParagraph,
  BannerPreview,
  NoBannerWrapper,
  NoRMCampainWrapper,
} from "../RMOptflowCommon.styled";
import { RMStyledTableWrapper } from "./RMEnrollUserstable.styles";
import { USBIconSort, USBIconInfo } from "@usb-shield/react-icons";
import { Spinner } from "../../spinner";
import { StyledParagraphCenter } from "../../ManageCampaign/ManageCampaign.styled";
import { useAppDispatch, useAppSelector } from "../../store/hooks";
import { 
  updateSelectionFromPage, 
  clearSelectedUsers, 
  setCampaignId 
} from "../../store/userSelectionSlice";

interface ControlOptions {
  controlled: boolean;
  handleForwardClick: () => void;
  handleBackwardClick: () => void;
}

export const RMUsersTable = ({
  campaignId,
  onResponseNotification,
  setShowNotification,
}: {
  campaignId: string;
  onResponseNotification: (bool: boolean) => void;
  setShowNotification: React.Dispatch<React.SetStateAction<boolean>>;
}) => {
  // Redux
  const dispatch = useAppDispatch();
  const selectedUsers = useAppSelector(state => state.userSelection.selectedUsers);
  
  // Set campaign ID in Redux when component mounts or campaignId changes
  useEffect(() => {
    dispatch(setCampaignId(campaignId));
  }, [dispatch, campaignId]);
  
  // Only call the query if campaignId is not empty
  const { data: usersData, isLoading: isDataLoading } =
    useGetCampaignEnrollUsersByIdQuery(campaignId, {
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

  const [unEnroll, setUnEnroll] = useState<{
    campaignId: string;
    unEnrollReason: string;
    additionalComments: string;
    usersList: any[];
  }>({
    campaignId: campaignId,
    unEnrollReason: "",
    additionalComments: "",
    usersList: [],
  });

  useEffect(() => {
    if (usersData?.usersList) {
      setFilteredData(usersData.usersList);
    }
  }, [usersData]);

  useEffect(() => {
    setUnEnroll((prev) => ({
      ...prev,
      campaignId: campaignId,
    }));
  }, [campaignId]);
  
  // Update unEnroll state when selectedUsers changes in Redux
  useEffect(() => {
    setUnEnroll((prev) => ({
      ...prev,
      usersList: selectedUsers,
    }));
    
    console.log("Selected users from Redux:", selectedUsers);
  }, [selectedUsers]);

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
    ],
    [sortConfig]
  );

  const tableData = useMemo(() => {
    let data =
      filteredData.length > 0 ? filteredData : usersData?.usersList || [];

    if (sortConfig) {
      data = [...data].sort((a, b) => {
        const aValue = a[sortConfig.key] || "";
        const bValue = b[sortConfig.key] || "";

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

  // Handle checkbox selection using Redux
  const handleRowSelectionChange = (newSelection: any) => {
    // Check if newSelection is a function and call it with the current state
    const updatedSelection =
      typeof newSelection === "function" ? newSelection(rowSelection) : newSelection;
  
    // Update the local rowSelection state for visual UI
    setRowSelection(updatedSelection);
  
    // If any checkbox is selected, hide the error message
    if (Object.keys(updatedSelection).length > 0) {
      setShowNotification(false);
    }
    
    // Dispatch to Redux with current page data and selection
    dispatch(updateSelectionFromPage({
      currentPageData,
      rowSelection: updatedSelection
    }));
    
    console.log("Updated row selection in Redux", updatedSelection);
  };

  // Handle page change
  const handlePageChange = (newPage: number) => {
    // Change page
    setPagination(prev => ({
      ...prev,
      pageIndex: newPage - 1,
    }));
    
    // Reset the current page's row selection state (visual checkboxes only)
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
      setPagination((prev) => ({ 
        ...prev, 
        pageIndex: prev.pageIndex + 1 
      }));
      
      // Clear row selection for the new page (visual only)
      setRowSelection({});
    },
    handleBackwardClick: () => {
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

  // Handle clearing all selections
  const handleUndoSelections = () => {
    dispatch(clearSelectedUsers());
    setRowSelection({});
  };

  // Render loading state conditionally
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
          VIEWING{" "}
          {isdata && filteredData.length === 0 ? 0 : currentPageData.length} of{" "}
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
              ...unEnroll,
              usersList: selectedUsers // Use selectedUsers from Redux
            }}
            actionType="unenroll"
            setRowSelection={setRowSelection}
            onResponseNotification={onResponseNotification}
            rowSelection={rowSelection}
            setShowNotification={setShowNotification}
            onUndoSelections={handleUndoSelections} // Pass the handler
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