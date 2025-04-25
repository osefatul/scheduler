import USBTable from "@usb-shield/react-table";
import { useState, useMemo, useEffect,  } from "react";
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
  const [selectedIndexMap, setSelectedIndexMap] = useState<Record<number, any[]>>({});

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

  // Now we can use currentPageData in useEffect
  useEffect(() => {
    if (Object.keys(rowSelection).length > 0 && currentPageData) {
      const selectedUsers = Object.keys(rowSelection)
        .map((rowId) => {
          const index = parseInt(rowId);
          return currentPageData[index];
        })
        .filter(Boolean);

      // Save current page selections
      setSelectedIndexMap(prev => ({
        ...prev,
        [pageIndex]: selectedUsers
      }));

      // Combine all selected users across pages
      const allSelectedUsers = Object.values(selectedIndexMap).flat();
      const combinedSelections = [...allSelectedUsers, ...selectedUsers]
        // Remove duplicates (using userName as unique identifier)
        .filter((user, index, self) => 
          index === self.findIndex(u => u.userName === user.userName)
        );

      setUnEnroll((prev) => ({
        ...prev,
        usersList: combinedSelections,
      }));

      console.log("Selected users for unenrollment:", combinedSelections);
    }
  }, [rowSelection, currentPageData, pageIndex]);

  // Update _data when tableData or pagination changes
  useEffect(() => {
    setData(currentPageData);
    
    // Option 1: Clear selection when changing page (uncomment if you want this behavior)
    // setRowSelection({});
    
    // Option 2: Keep track of selections across pages (this is implemented via selectedIndexMap)
  }, [currentPageData]);

  // Handle checkbox selection
  const handleRowSelectionChange = (newSelection: any) => {
    // Check if newSelection is a function and call it with the current state
    const updatedSelection =
      typeof newSelection === "function" ? newSelection(rowSelection) : newSelection;
  
    console.log("Row selection changed:", updatedSelection); // Debugging log
    console.log("Type of updatedSelection:", typeof updatedSelection); // Check the type
    console.log("Keys in updatedSelection:", Object.keys(updatedSelection || {})); // Check the keys
  
    // Update the rowSelection state
    setRowSelection(updatedSelection);
  
    // If any checkbox is selected, hide the error message
    if (Object.keys(updatedSelection).length > 0) {
      console.log("Selected rows:", updatedSelection);
      setShowNotification(false);
    }
  };

  // Handle page change
  const handlePageChange = (newPage: number) => {
    // Save current page selections before changing page
    if (Object.keys(rowSelection).length > 0) {
      const currentSelections = Object.keys(rowSelection)
        .map(rowId => {
          const index = parseInt(rowId);
          return currentPageData[index];
        })
        .filter(Boolean);
      
      setSelectedIndexMap(prev => ({
        ...prev,
        [pageIndex]: currentSelections
      }));
    }
    
    // Change page
    setPagination(prev => ({
      ...prev,
      pageIndex: newPage - 1,
    }));
    
    // Clear current page selection state
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
      if (Object.keys(rowSelection).length > 0) {
        const currentSelections = Object.keys(rowSelection)
          .map(rowId => {
            const index = parseInt(rowId);
            return currentPageData[index];
          })
          .filter(Boolean);
        
        setSelectedIndexMap(prev => ({
          ...prev,
          [pageIndex]: currentSelections
        }));
      }

      setPagination((prev) => ({ 
        ...prev, 
        pageIndex: prev.pageIndex + 1 
      }));
      
      // Clear row selection for the new page
      setRowSelection({});
    },
    handleBackwardClick: () => {
      // Save current page selections before going backward
      if (Object.keys(rowSelection).length > 0) {
        const currentSelections = Object.keys(rowSelection)
          .map(rowId => {
            const index = parseInt(rowId);
            return currentPageData[index];
          })
          .filter(Boolean);
        
        setSelectedIndexMap(prev => ({
          ...prev,
          [pageIndex]: currentSelections
        }));
      }

      setPagination((prev) => ({
        ...prev,
        pageIndex: Math.max(prev.pageIndex - 1, 0),
      }));
      
      // Clear row selection for the new page
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
    setTest(temp)

    if (temp.length === 0) {
      console.log("No matching records founddsadsdsd");
    }
    setFilteredData(temp);
    setPagination((prev) => ({ ...prev, pageIndex: 0 })); // Reset to the first page
    setIsdata(true)
  };

  // Optional: Add a useEffect to log changes to unEnroll
  useEffect(() => {
    console.log("Unenroll state updated:", unEnroll);
  }, [unEnroll, rowSelection]);
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
            usersData={unEnroll}
            actionType="unenroll"
            setRowSelection={setRowSelection}
            onResponseNotification={onResponseNotification}
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