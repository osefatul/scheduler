import { useState, useEffect, useMemo } from "react";
import USBTable from "@usb-shield/react-table";
import { getCoreRowModel } from "@tanstack/react-table";
import { useGetCampaignEnrollUsersByIdQuery } from "@/internal/services/rmCampaignUsersAPI";
import RMtabFooter from "../RMTabFooter/RMTabFooter";
import RMPagination from "../RMoptoutCampaignList/RMoptoutCampaignList.styles";
import USBPagination from "@usb-shield/react-pagination";
import USBButton from "@usb-shield/react-button";
import USBSearchInput from "@usb-shield/react-search-input";
import { RMSearch } from "../RMSearchUsers/RMSearchUsers.styles";
import {
  StyledParagraph,
  BannerPreview,
  NoBannerWrapper
} from "../RMOptflowCommon.styled";
import { RMStyledTableWrapper } from "./RMEnrollUsersTable.styles";
import {
  USBIconSort,
  USBIconInfo
} from "@usb-shield/react-icons";

interface ControlOptions {
  controlled: boolean;
  handleForwardClick: () => void;
  handleBackwardClick: () => void;
}

export const RMUsersTable = ({
  campaignId,
  onHideNotification,
  setShowNotification,
}: {
  campaignId: string;
  onHideNotification: (bool: boolean) => void; // Callback function to hide the notification
  setShowNotification: React.Dispatch<React.SetStateAction<boolean>>;
}) => {
  // Optional: Add a useEffect to log changes to unEnroll
  useEffect(() => {
  }, [unEnroll, rowSelection]);
  
  // Render loading state conditionally
  if (!userData) {
    return <div>Loading...</div>;
  }

  // Only call the query if campaignId is not empty
  const { data: userData, userdata } = useGetCampaignEnrollUsersByIdQuery(campaignId, {
    skip: !campaignId, // Skip the query if campaignId is empty
  });

  const [sortConfig, setSortConfig] = useState({
    key: "string",
    direction: "asc" || "desc",
  } | null)(null);

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

  const columns = useMemo(() => [
    {
      header: {
        <div className="header" onClick={() => handleSort("userName")}>
          Username <USBIconSort />
        </div>
      },
      accessorKey: "userName",
    },
    {
      header: {
        <div className="header" onClick={() => handleSort("companyName")}>
          Company name <USBIconSort />
        </div>
      },
      accessorKey: "companyName",
    },
    {
      header: {
        <div className="header" onClick={() => handleSort("emailId")}>
          Email ID <USBIconSort />
        </div>
      },
      accessorKey: "emailId",
    },
    {
      header: {
        <div className="header" onClick={() => handleSort("name")}>
          Name <USBIconSort />
        </div>
      },
      accessorKey: "name",
    },
  ], [sortConfig]);

  const [searchInputValue, setSearchInputValue] = useState("");
  const [filteredData, setFilteredData] = useState<any[]>([]);

  const tableData = useMemo(() => {
    let data = filteredData.length > 0 ? filteredData : userData?.usersList || [];
    if (sortConfig) {
      data = [...data].sort((a, b) => {
        if (a[sortConfig.key] < b[sortConfig.key]) {
          return sortConfig.direction === "asc" ? -1 : 1;
        }
        if (a[sortConfig.key] > b[sortConfig.key]) {
          return sortConfig.direction === "asc" ? 1 : -1;
        }
        return 0;
      });
    }
    return data;
  }, [userData, filteredData, sortConfig]);

  useEffect(() => {
    if (userData?.usersList) {
      setFilteredData(userData.usersList); // Initialize filteredData with all records
    }
  }, [userData]);

  //const searchInputRef = useRef<HTMLInputElement>(null); // Create a ref for the search input
  const [_data, setData] = useState(tableData);
  const [test, setTest] = useState("");
  const [isData, setIsData] = useState(false);
  const [{ pageIndex, pageSize }, setPagination] = useState({
    pageIndex: 0,
    pageSize: 10, // Show 10 rows per page
  });

  // Calculate the data for the current page
  const currentPageData = useMemo(() => {
    const startIndex = pageIndex * pageSize;
    return tableData.slice(startIndex, startIndex + pageSize);
  }, [tableData, pageIndex, pageSize]);

  useEffect(() => {
    setData(tableData);
  }, [tableData, currentPageData]);

  const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

  // Handle checkbox selection
  const handleRowSelectionChange = (newSelection: Record<string, boolean>) => {
    setRowSelection(newSelection);
    
    // If any checkbox is selected, disable the error message
    if (Object.keys(newSelection).length > 0) {
      setShowNotification(false);
    }
  };

  const [unEnroll, setUnEnroll] = useState({
    campaignId: string;
    unEnrollReason: string;
    additionalComments: string;
    usersList: any[];
  })({
    campaignId: campaignId,
    unEnrollReason: "",
    additionalComments: "",
    usersList: [],
  });

  const optionsVB = {
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
    handleForwardClick: () =>
      setPagination((prev) => ({
        ...prev,
        pageIndex: prev.pageIndex + 1
      })),
    handleBackwardClick: () =>
      setPagination((prev) => ({
        ...prev,
        pageIndex: Math.max(prev.pageIndex - 1, 0)
      })),
  };

  // Update the handleSearchButtonClick function to match the expected type
  const handleSearchButtonClick = () => {
    console.log("Search form submitted:", searchInputValue);
    if (!searchInputValue) {
      // If the search input is empty, reset the filtered data to show all records
      setFilteredData(userData?.usersList || []); // Reset to all records
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
    setIsData(true);
  };

  return (
    <RMStyledTableWrapper>
      <RMSearch>
        <USBSearchInput
          type="search"
          placeholder="Search by user name or companyname"
          handleChange={(e: React.ChangeEvent<HTMLInputElement>) => {
            const value = e.target.value;
            setSearchInputValue(value);
            
            // If the input is cleared, reset the filtered data to show all records
            if (value.length === 0) {
              setFilteredData(userData?.usersList || []); // Reset to all records
              setPagination((prev) => ({ ...prev, pageIndex: 0 })); // Reset to the first page
              setIsData(false); // Reset the "isdata" flag
            }
          }}
          onKeyPress={(e: React.KeyboardEvent<HTMLInputElement>) => {
            if (e.key === "Enter") {
              handleSearchButtonClick(); // Trigger search on Enter key press
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
            tableData.length > 0
              ? "Total available " + tableData.length + " search results"
              : "Search"
          }
          handleClick={handleSearchButtonClick}
        >
          Search
        </USBButton>
        <StyledParagraph>
          VIEWING {isData && filteredData.length === 0 ? 0 : currentPageData.length > 0 ? currentPageData.length : 0} OF {' '}
          {filteredData.length > 0 ? filteredData.length : 0} USERS
        </StyledParagraph>
      </RMSearch>

      {isData && test.length === 0 ? (
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
            options={optionsVB}
            borders="none"
            isZebraStriped={true}
            batchActionsBar
            toolbarActions={[
              {
                type: "utility",
                text: "Export",
                size: "small",
                clickEvent: () => {
                  const selectedRows = Object.keys(rowSelection).map(
                    (rowId) => _data[rowId]
                  );
                  setUnEnroll((prev) => ({
                    ...prev,
                    usersList: selectedRows,
                  }));
                },
                id: "primary-button-test-id",
              },
            ]}
          />

          <RMPagination>
            <USBPagination
              currentPage={filteredData.length === 0 ? 1 : pageIndex + 1} // Show page 1 when no matching records
              pageSelectionType="menu"
              totalCount={
                filteredData.length === 0
                  ? 1
                  : Math.ceil(filteredData.length / pageSize)
              } // Show 1 page when no matching records
              handlePageChange={(newPage: number) => {
                console.log("Page changed to:", newPage);
                setPagination((prev) => ({
                  ...prev,
                  pageIndex: newPage - 1, // Convert 1-based index to 0-based index
                }));
              }}
              paginationAriaLabel="Pagination Navigation"
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

          <RMtabFooter
            userData={unEnroll}
            actionType="unenroll"
            setRowSelection={setRowSelection}
            onHideNotification={onHideNotification}
            rowSelection={rowSelection}
            setShowNotification={setShowNotification}
          />
        </>
      ) : (
        <div style={{ textAlign: "center", marginTop: "20px" }}>
          No matching records found.
        </div>
      )}
    </RMStyledTableWrapper>
  );
};

export default RMUsersTable;