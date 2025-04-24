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
  onHideNotification,
}: {
  campaignId: string;
  onHideNotification: (bool: boolean) => void;
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
  const [test, setTest] = useState('');
  const [isdata, setIsdata] = useState(false);
  const [showNotification, setShowNotification] = useState(false);
  const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});
  const [{ pageIndex, pageSize }, setPagination] = useState({
    pageIndex: 0,
    pageSize: 10,
  });

  // Initialize enroll state with empty array (not null)
  const [enroll, setEnroll] = useState<{
    campaignId: string;
    usersList: any[];
  }>({
    campaignId: campaignId,
    usersList: [], // Initialize with empty array instead of null
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

  // Update usersList in enroll state when rowSelection changes
  useEffect(() => {
    if (currentPageData && currentPageData.length > 0) {
      const selectedUsers = Object.keys(rowSelection).map(rowId => {
        const index = parseInt(rowId);
        return currentPageData[index];
      }).filter(Boolean);
      
      setEnroll(prev => ({
        ...prev,
        usersList: selectedUsers
      }));
      
      if (selectedUsers.length > 0) {
        console.log("Selected users for enrollment:", selectedUsers);
      }
    }
  }, [rowSelection, currentPageData]);

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
  const handleRowSelectionChange = (newSelection: Record<string, boolean>) => {
    setRowSelection(newSelection);
    
    // If any checkbox is selected, hide the error message
    if (Object.keys(newSelection).length > 0) {
      setShowNotification(false);
    }
  };

  const handleSearchButtonClick = () => {
    if (!searchInputValue) {
      // If search input is empty, reset to show all records
      setFilteredData(usersData?.usersList || []);
      setPagination((prev) => ({ ...prev, pageIndex: 0 }));
      setIsdata(false);
      return;
    }

    const searchTerm = searchInputValue.toLowerCase();
    const filteredResults = (usersData?.usersList || []).filter((item: any) => {
      return (
        (item.userName || '').toLowerCase().includes(searchTerm) ||
        (item.emailId || '').toLowerCase().includes(searchTerm) || 
        (item.name || '').toLowerCase().includes(searchTerm) || 
        (item.companyName || '').toLowerCase().includes(searchTerm)
      );
    });
    
    setTest(filteredResults);
    setFilteredData(filteredResults);
    setPagination((prev) => ({ ...prev, pageIndex: 0 }));
    setIsdata(true);
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
    handleForwardClick: () =>
      setPagination((prev) => ({ ...prev, pageIndex: prev.pageIndex + 1 })),
    handleBackwardClick: () =>
      setPagination((prev) => ({ ...prev, pageIndex: Math.max(prev.pageIndex - 1, 0) })),
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
            toolBarActions={[
              {
                type: "utility",
                text: "Export",
                size: "small",
                clickEvent: () => {
                  // This custom action is not needed for updating usersList
                  // as we now update it directly in the useEffect when rowSelection changes
                  console.log("Export button clicked");
                },
                id: "primary-button-test-id-enroll",
              },
            ]}
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
              handlePageChange={(newPage: number) => {
                setPagination((prev) => ({
                  ...prev,
                  pageIndex: newPage - 1,
                }));
              }}
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
            usersData={enroll}
            actionType="enroll"
            setRowSelection={setRowSelection}
            onHideNotification={onHideNotification}
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