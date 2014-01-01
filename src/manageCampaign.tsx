import React, { useEffect, useState, useRef } from "react";
import {
  CompainBuilder,
  MapcontentInsight,
  CompainDropdownTable,
  MapInsightTable,
  BannerPreview,
  BannerReviewContainer,
  ButtonRow,
  ContentMainCard,
  ContentWrapper,
  HeaderSection,
  PageContainer,
  PreviewSection,
  ReviewMainCard,
  ScheduleCampaignMainCard,
  ScheduleCampaignContainer,
  UserMainReviewCard,
  UserReviewContainer,
} from "./ManageCampaign.styled";

import TestDatePicker from './TestDatePicker';
import RenderInsights from "./TableView"; 
import USBTextInput from '@usb-shield/react-forms-input-text';
import USBSelect from '@usb-shield/react-forms-select';
import USBRadioGroup from '@usb-shield/react-forms-radio-group';
import USBDropdown from '@usb-shield/react-dropdown';
import {
  USBIconEdit,
  USBIconInfo,
  USBIconAdd
} from "@usb-shield/react-icons";
import { USBText } from "@usb-shield/addon-react-primitives/text";
import USBButton from "@usb-shield/react-button";
import USBModal, {
  ModalHeader,
  ModalBody,
  ModalFooter,
} from "@usb-shield/react-modal";
import {
  ApiResponse,
  Banner,
  BannersResponse,
  RadiobtnEvent,
  MapContentToUserPayload,
  MapContentUsersBatchPayload,
} from "./IManageCampaign";
import { 
  useGetInsightsQuery, 
  useMapContentToUserMutation, 
  useMapContentUserBatchMutation 
} from "@/internal/services/contentAPI";
import { 
  useGetInsightTypesQuery, 
  useGetInsightSubTypesQuery, 
  useGetInsightsByTypeAndSubTypeQuery, 
  useSearchInsightsQuery 
} from "@/internal/services/insightAPI";
import { 
  useCreateCampaignMutation,
  useGetAllCampaignsQuery,
  CampaignRequest 
} from "@/internal/services/campaignAPI";
import { USBBanner } from "../banner";
import { Spinner } from "../spinner";
import CampaignList from "./CampaignList";

const ManageCampaign: React.FC = () => {
  const [currentStep, setCurrentStep] = useState<number>(0);
  const [isVersionModalOpen, setIsVersionModalOpen] = useState<boolean>(false);
  const [selectedBanners, setSelectedBanners] = useState<string[]>([]);
  const [selectedContentIds, setSelectedContentIds] = useState<string[]>([]);
  const [foundUsers, setFoundUsers] = useState<string[]>([]);
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([]);
  const [modalMessage, setModalMessage] = useState<string>("");
  const [modalSuccess, setModalSuccess] = useState<boolean>(true);
  const [editingBanners, setEditingBanners] = useState<boolean>(false);
  const [editingUsers, setEditingUsers] = useState<boolean>(false);
  const [campaignName, setCampaignName] = useState<string>("");
  const [submissionSuccess, setSubmissionSuccess] = useState<boolean>(false);
  
  // Insight dropdown states
  const [selectedInsightType, setSelectedInsightType] = useState<string | null>(null);
  const [selectedSubType, setSelectedSubType] = useState<string | null>(null);
  const [selectedInsight, setSelectedInsight] = useState<string | null>(null);
  const [searchTriggered, setSearchTriggered] = useState<boolean>(false);
  
  // Fetch data using RTK Query
  const { data: insightTypesData, isLoading: isLoadingTypes } = useGetInsightTypesQuery();
  
  const { data: insightSubTypesData, isLoading: isLoadingSubTypes } = 
    useGetInsightSubTypesQuery(selectedInsightType || '', { 
      skip: !selectedInsightType 
    });
  
  const { data: insightsData, isLoading: isLoadingInsights } = 
    useGetInsightsByTypeAndSubTypeQuery(
      { 
        insightType: selectedInsightType || '', 
        insightSubType: selectedSubType || '' 
      }, 
      { 
        skip: !selectedInsightType || !selectedSubType 
      }
    );
  
  const { data: searchData, isLoading: isLoadingSearch } = 
    useSearchInsightsQuery(
      {
        insightType: selectedInsightType || undefined,
        insightSubType: selectedSubType || undefined,
        insight: selectedInsight || undefined
      },
      { 
        skip: !searchTriggered || !selectedInsightType 
      }
    );

  // Campaign creation mutation
  const [createCampaign, { isLoading: isSubmittingCampaign }] = useCreateCampaignMutation();
  const { refetch: refetchCampaigns } = useGetAllCampaignsQuery();

  // Format dropdown items
  const insightTypes = insightTypesData?.map((type: string) => ({
    id: type,
    value: type
  })) || [];

  const insightSubTypes = insightSubTypesData?.map((subtype: string) => ({
    id: subtype,
    value: subtype
  })) || [];

  const insights = insightsData?.map((insight: string) => ({
    id: insight,
    value: insight
  })) || [];

  // Insight data for tables
  const [insightdataRows, setInsightdataRows] = useState<string[][]>([
    ["--", "--", "--"],
  ]);

  const [eligibleCompanies, setEligibleCompanies] = useState<number>(0);
  const [eligibleUsers, setEligibleUsers] = useState<number>(0);
  const [conversionPossibility, setConversionPossibility] = useState<string>('NA');
  const [cmpuserdataRows, setCmpuserdataRows] = useState<string[][]>([
    ["0", "0", "Select filters and apply"],
  ]);

  const insightHeadings = ["Insight Type", "Insight Subtype", "Insight"];
  const cmpuserHeadings = ["Companies ", "Users", "Status"];

  const selectRef = useRef(null);
  const handleStatusUpdate = () => {console.log()};
  const selectErrorMessages = { default: 'Please complete this field.' };
  const monthsSchedule = [
    { value: '1', content: '1' },
    { value: '3', content: '3' },
    { value: '6', content: '6' },
    { value: '9', content: '9' },
    { value: '12', content: '12' },
    { value: '18', content: '18' },
  ];

  // Scheduling states
  const [selectedDuration, setSelectedDuration] = useState<string>("1");
  const [selectedFrequency, setSelectedFrequency] = useState<string>("1");
  const [selectedCapping, setSelectedCapping] = useState<string>("1");
  const [startDate, setStartDate] = useState<string>("05/22/2024");

  // Handle Insight Type change
  const handleInsightTypeChange = (id: string) => {
    console.log("Insight Type Changed:", id);
    setSelectedInsightType(id);
    setSelectedSubType(null); // Reset selected subtype
    setSelectedInsight(null); // Reset selected insight
    setSearchTriggered(false); // Reset search state
  };

  // Handle Subtype change
  const handleSubtypeChange = (id: string) => {
    console.log("Subtype Changed:", id);
    setSelectedSubType(id);
    setSelectedInsight(null); // Reset selected insight
    setSearchTriggered(false); // Reset search state
  };

  // Handle Insight change
  const handleInsightChange = (id: string) => {
    console.log("Insight Changed:", id);
    setSelectedInsight(id);
    setSearchTriggered(false); // Reset search state
  };

  // Handle Apply button click
  const handleApplyFilters = () => {
    setSearchTriggered(true);
  };

  // Update eligible companies and users when search data changes
  useEffect(() => {
    if (searchData && searchTriggered) {
      const totalCompanies = new Set(searchData.map((item: any) => item.topParentName)).size;
      setEligibleCompanies(totalCompanies);
      setEligibleUsers(searchData.length);
      
      // Update data rows for the table displays
      if (selectedInsightType && selectedSubType && selectedInsight) {
        setInsightdataRows([[selectedInsightType, selectedSubType, selectedInsight]]);
      }
      
      const status = searchData.length > 0 ? "Good to go" : "No eligible users";
      setCmpuserdataRows([[totalCompanies.toString(), searchData.length.toString(), status]]);
    }
  }, [searchData, searchTriggered, selectedInsightType, selectedSubType, selectedInsight]);

  const { data: BannersResponse, isLoading: isContentsListLoading } =
    useGetInsightsQuery<{ data: BannersResponse; isLoading: boolean }>();

  const [mapContentToUser, { isLoading: isMapContentToUserLoading }] =
    useMapContentToUserMutation<{
      arg: MapContentToUserPayload;
      result: ApiResponse;
      isLoading: boolean;
    }>();
    
  const [mapContentUsersBatch, { isLoading: isMapContentToUsersLoading }] =
    useMapContentUserBatchMutation<{
      arg: MapContentUsersBatchPayload;
      result: ApiResponse;
      isLoading: boolean;
    }>();

  useEffect(() => {
    if (BannersResponse?.data) {
      const contentIds = BannersResponse.data
        .filter((banner: any) => selectedBanners.includes(banner.title))
        .map((banner: any) => banner.id);

      setSelectedContentIds(contentIds);
    }
  }, [selectedBanners, BannersResponse]);

  useEffect(() => {
    setSelectedUserIds(foundUsers);
  }, [foundUsers]);

  const handleRadioChange = (selected: any[]) => {
    console.log(selected);
    setSelectedBanners([`${selected}`]);
  };

  const handleRemoveBanner = (bannerTitle: string) => {
    setSelectedBanners((prev) => prev.filter((title) => title !== bannerTitle));
  };

  const toggleEditingBanners = () => {
    setEditingBanners(!editingBanners);
  };

  const toggleEditingUsers = () => {
    setEditingUsers(!editingUsers);
  };

  // Updated handleConfirm function with campaign submission
  const handleConfirm = async () => {
    if (!selectedBanners.length || !selectedInsightType) {
      setModalSuccess(false);
      setModalMessage("Please select at least one banner and complete the insight selection.");
      setIsVersionModalOpen(true);
      return;
    }

    try {
      // First, prepare the campaign data
      const selectedBanner = selectedBannerData[0]; // Assuming single selection
      
      // Parse date from MM/DD/YYYY format to ISO string
      const dateParts = startDate.split('/');
      const formattedDate = new Date(
        parseInt(dateParts[2]), 
        parseInt(dateParts[0]) - 1, 
        parseInt(dateParts[1])
      ).toISOString();
      
      const campaignData: CampaignRequest = {
        name: campaignName || `Campaign for ${selectedBanner.title}`,
        bannerId: selectedBanner.id,
        insightType: selectedInsightType || '',
        insightSubType: selectedSubType || '',
        insight: selectedInsight || '',
        eligibleCompanies: eligibleCompanies,
        eligibleUsers: eligibleUsers,
        startDate: formattedDate,
        durationMonths: parseInt(selectedDuration),
        frequencyPerWeek: parseInt(selectedFrequency),
        displayCapping: parseInt(selectedCapping),
        displayLocation: 'Corporate Connect dashboard',
        createdBy: 'adminUser' // Replace with actual user ID from auth system
      };

      // Submit the campaign
      const response = await createCampaign(campaignData).unwrap();
      
      setSubmissionSuccess(true);
      setModalSuccess(true);
      setModalMessage(`Campaign "${response.name}" has been successfully scheduled and will start on ${new Date(response.startDate).toLocaleDateString()}.`);
      setIsVersionModalOpen(true);
      
    } catch (error: any) {
      console.error("Campaign submission error:", error);
      const errorMsg = error?.data?.message || error?.message || "Failed to create campaign";
      
      setSubmissionSuccess(false);
      setModalSuccess(false);
      setModalMessage(errorMsg);
      setIsVersionModalOpen(true);
    }
  };

  // Banner renderer function
  const renderBannerItem = (
    banner: Banner,
    showRemoveButton: boolean = false
  ) => {
    const { id, title, message, bannerImageUrl, bannerBackgroundColor } =
      banner;

    return (
      <USBBanner
        key={id}
        id={id}
        title={title}
        message={message}
        bannerImageUrl={bannerImageUrl}
        bannerbackgroundcolor={bannerBackgroundColor}
        showRemoveButton={showRemoveButton}
        onRemove={() => handleRemoveBanner(title)}
        width={undefined}
        maxWidth={undefined}
        height={undefined}
        removeButton={undefined}
        marginBottom={undefined}
        containerWidth={undefined}
      />
    );
  };

  const selectedBannerData =
    BannersResponse?.data?.filter((banner: any) =>
      selectedBanners.includes(banner.title)
    ) || [];

  const isLoading =
    isContentsListLoading ||
    isMapContentToUserLoading ||
    isMapContentToUsersLoading ||
    isLoadingTypes ||
    isLoadingSubTypes ||
    isLoadingInsights ||
    isSubmittingCampaign ||
    (isLoadingSearch && searchTriggered);

  const resetForm = () => {
    setSelectedBanners([]);
    setSelectedContentIds([]);
    setFoundUsers([]);
    setSelectedUserIds([]);
    setSelectedInsightType(null);
    setSelectedSubType(null);
    setSelectedInsight(null);
    setCampaignName("");
    setStartDate("05/22/2024");
    setSelectedDuration("1");
    setSelectedFrequency("1");
    setSelectedCapping("1");
    setEligibleCompanies(0);
    setEligibleUsers(0);
    setSearchTriggered(false);
    refetchCampaigns(); // Refresh the campaigns list
  };

  // Modal close handler
  const handleModalClose = () => {
    setIsVersionModalOpen(false);
    
    if (submissionSuccess) {
      // Reset the form and go back to the campaign list
      resetForm();
      setCurrentStep(0);
    }
  };

  // Handle date change from the date picker
  const handleDateChange = (e: { inputValue: string }) => {
    setStartDate(e.inputValue);
  };

  return (
    <React.Fragment>
      {isLoading && <Spinner />}

      <PageContainer>
        <ContentWrapper>
          {currentStep === 0 && (
              <>
              <HeaderSection>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h1>Manage campaign</h1>
                
                <USBButton
                  emphasis="subtle"
                  ctaStyle="standard"
                  handleClick={() => setCurrentStep(1)}
                >
                 <USBIconAdd size={20} />
                  Set up campaign
                </USBButton>
                </div>
                <p>
                Set up campaigns for targeted user groups. All past and scheduled campaigns will appear in the following table
                </p>  
              </HeaderSection>
              
              {/* Campaign List Component */}
              <CampaignList />
              </>
          )}
          {currentStep === 1 && (
            <>
              <HeaderSection>
                <h1>Set up a campaign</h1>
                <p>
                Use pre-designed banners, define your audience and schedule campaigns.
                </p>
              </HeaderSection>

              <p className="section-title">Choose a banner</p>

              <ContentMainCard>
                <Sidebar>
                
                <USBRadioGroup 
                 legendText="Choose banner content"
                inputName="banners" 
                
                statusUpdateCallback={(event: RadiobtnEvent) => {
                  handleRadioChange(
                    event?.inputValue
                  );
                }}
                errorMessages={{
                  default: "Select at least one banner.",
                }}
                isDisabled={false}
                dynamicValue={selectedBanners[0] || ""}
                options={BannersResponse?.data?.map((banner: any) => ({
                  value: banner.title,
                  label: banner.title,
                  disabled: false,
  
                }))}
                
                />

                </Sidebar>

                <PreviewSection>
                  <h2>Preview banner</h2>
                  {!selectedBannerData || selectedBannerData.length === 0 ? (
                    <BannerPreview>
                      <div className="no-banner">
                        <USBIconInfo size={20} />
                        <p>You don't have any campaigns selected</p>
                      </div>
                    </BannerPreview>
                  ) : (
                    <div
                      style={{
                        display: "flex",
                        flexDirection: "column",
                        gap: "20px",
                      }}
                    >
                      {selectedBannerData.map((banner: any) =>
                        renderBannerItem(banner)
                      )}
                    </div>
                  )}
                </PreviewSection>
              </ContentMainCard>
              <h2 style={{marginTop:'1.5rem',marginBottom:'1.5rem',fontWeight: '500'}}>Map the selected content to insight</h2>
               
              <MapcontentInsight>
                <USBDropdown 
                  dropdownType="text" 
                  items={insightTypes}
                  labelText="Insight type" 
                  addClasses="dropdown_insighttype custom-dropdown" 
                  emphasis="heavy" 
                  handleChange={(event: any) => handleInsightTypeChange(event.target.id)}
                  defaultSelected={selectedInsightType || ''}
                  isDisabled={isLoadingTypes}
                />
                <USBDropdown                 
                  dropdownType="text" 
                  items={insightSubTypes.length > 0 ? insightSubTypes : [{id: '', value: 'Select insight type first', disable: true }]}
                  labelText="Insight subtype" 
                  addClasses="dropdown_insightsubtype" 
                  emphasis="heavy"
                  handleChange={(event: any) => handleSubtypeChange(event.target.id)}
                  defaultSelected={selectedSubType || ''}
                  isDisabled={!selectedInsightType || isLoadingSubTypes}
                />
                <USBDropdown                 
                  dropdownType="text" 
                  items={insights.length > 0 ? insights : [{id: '', value: 'Select subtype first', disable: true }]}
                  labelText="Insight" 
                  addClasses="dropdown_insight" 
                  emphasis="heavy" 
                  handleChange={(event: any) => handleInsightChange(event.target.id)}
                  defaultSelected={selectedInsight || ''}
                  isDisabled={!selectedSubType || isLoadingInsights}
                /> 
                <USBButton
                  emphasis="subtle"
                  ctaStyle="standard"
                  handleClick={handleApplyFilters}
                  disabled={!selectedInsightType || isLoadingSearch}
                >
                  Apply
                </USBButton>              
              </MapcontentInsight>
              
              <h2 style={{marginTop:'1.5rem', fontWeight: '500'}}>Eligible companies and users</h2>
              <MapInsightTable style={{marginTop:'1.5rem'}}>
                <CompainDropdownTable>
                  <div className="insight-row">
                    <p className="dropdown_insighttype custom-dropdown">Companies</p>
                    <span className="dropdown_value">{eligibleCompanies}</span>
                  </div>
                  <div className="insight-row">
                    <p className="dropdown_insightsubtype">Users</p>
                    <span className="dropdown_value">{eligibleUsers}</span>
                  </div>
                  <div className="insight-row">
                    <p className="dropdown_insight">Conversion possibility</p>
                    <span className="dropdown_value">{conversionPossibility}</span>
                  </div>
                </CompainDropdownTable>
                  
              </MapInsightTable> 
              <ButtonRow>
                <USBButton
                  emphasis="minimal"
                  ctaStyle="standard"
                  handleClick={() => setCurrentStep(0)}
                >
                  Cancel
                </USBButton>
                <USBButton
                  emphasis="heavy"
                  ctaStyle="standard"
                  handleClick={() => setCurrentStep(2)}
                  disabled={selectedBanners.length === 0}
                >
                  Continue
                </USBButton>
              </ButtonRow>
            </>
          )}
         
           {currentStep === 2 && (
            <>
              <HeaderSection>
                <h1>Set up a campaign.</h1>
                <p>Use pre-designed banners, define your audience and schedule campaigns.</p>
              </HeaderSection>

              <p className="section-title">Schedule campaign</p>

              <ScheduleCampaignMainCard>
                <ScheduleCampaignContainer>
                  <USBTextInput
                    errorMessages={{}}
                    inputName="campaign-name"
                    labelText="Campaign Name"
                    initValue={campaignName}
                    statusUpdateCallback={(e: { inputValue: string }) => {
                      setCampaignName(e.inputValue);
                    }}
                  />
                
                  {/* Date Picker with handler for updating state */}
                  <USBDatePicker
                    dataTestId="date-input-test-id"
                    dynamicInputValue={startDate}
                    statusUpdateCallback={handleDateChange}
                    dateFormat="mm/dd/yyyy"
                    content={{
                      dateInput: {
                        labelText: 'Start date',
                        helperText: 'mm/dd/yyyy',
                        errorMessages: {
                          default: 'Enter a valid startDate',
                        },
                      },
                    }}
                    settings={{
                      dateInput: {
                        inputName: 'fromDate',
                        initValue: startDate,
                      },
                      calendar: {
                        areWeekendsDisabled: true,
                        areBankHolidaysDisabled: true,
                        occurrence: 'past',
                      },
                    }}
                  />
                  
                  <USBSelect
                    ref={selectRef}
                    inputName='Duration (in months)'
                    labelText='Duration (in months)'
                    statusUpdateCallback={(e: any) => setSelectedDuration(e.selectedId)}
                    errorMessages={selectErrorMessages}
                    optionsArray={monthsSchedule}
                  />
                  <USBSelect
                    ref={selectRef}
                    inputName='Display frequency per week'
                    labelText='Display frequency per week'
                    statusUpdateCallback={(e: any) => setSelectedFrequency(e.selectedId)}
                    errorMessages={selectErrorMessages}
                    optionsArray={monthsSchedule}
                  />
                  <USBSelect
                    ref={selectRef}
                    inputName='Display capping'
                    labelText='Display capping'
                    statusUpdateCallback={(e: any) => setSelectedCapping(e.selectedId)}
                    errorMessages={selectErrorMessages}
                    optionsArray={monthsSchedule}
                  />

                  <div className="input-preview-wrapper">
                    <USBTextInput
                      errorMessages={{}}
                      initValue="Corporate Connect dashboard"
                      inputMode="numeric"
                      inputName="Corporate Connect dashboard"
                      isOptional
                      isReadOnly
                      labelText="Display Location"
                    />
                    <div className="preview-action">
                      <span className="preview-text">Preview</span>
                    </div>
                  </div>
                </ScheduleCampaignContainer>
              </ScheduleCampaignMainCard>
 
              <ButtonRow>
                <USBButton
                  emphasis="subtle"
                  ctaStyle="standard"
                  handleClick={() => setCurrentStep(1)}
                >
                  Back
                </USBButton>

                <USBButton
                  emphasis="heavy"
                  ctaStyle="standard"
                  handleClick={() => setCurrentStep(3)}
                >
                  Review Mapping
                </USBButton>
              </ButtonRow>
            </>
          )}

          {currentStep === 3 && (
            <ReviewMainCard>
              <HeaderSection>
                <h1>Review and submit</h1>
                <p>
                  Review your selected banners and tagged users before
                  confirming.
                </p>
              </HeaderSection>

              <UserMainReviewCard>
                <div className="edit-comp">
                  <h2>Selected Banners</h2>

                  <div className="edit-action" onClick={toggleEditingBanners}>
                    <USBIconEdit
                      addClasses="edit-icon"
                      style={{ color: "#235AE4" }}
                      size={20}
                    />
                    <span className="edit-text">
                      {editingBanners ? "Done" : "Edit"}
                    </span>
                  </div>
                </div>
                <BannerReviewContainer>
                  <div className="banner-list">
                    {!selectedBannerData || selectedBannerData.length === 0 ? (
                      <p>No banners selected.</p>
                    ) : (
                      selectedBannerData.map((banner: any) =>
                        renderBannerItem(banner, editingBanners)
                      )
                    )}
                  </div>
                </BannerReviewContainer>
              </UserMainReviewCard>

              <UserMainReviewCard>
                <div className="edit-comp">
                  <USBText as="h2">Selected Insights</USBText>

                  <div className="edit-action" onClick={toggleEditingUsers}>
                    <USBIconEdit
                      addClasses="edit-icon"
                      style={{ color: "#235AE4" }}
                      size={20}
                    />
                    <span className="edit-text">
                      {editingUsers ? "Done" : "Edit"}
                    </span>
                  </div>
                </div>
                <UserReviewContainer>
                  {!selectedInsightType ? (
                    <p>No insight details.</p>
                  ) : editingUsers ? (
                    <p>Editing insights...</p>
                  ) : (
                    <RenderInsights columnHeadings={insightHeadings} dataRows={insightdataRows} />
                  )}
                </UserReviewContainer>
              </UserMainReviewCard>

              <UserMainReviewCard>
                <div className="edit-comp">
                  <USBText as="h2">Eligible companies and users</USBText>

                  <div className="edit-action" onClick={toggleEditingUsers}>
                    <USBIconEdit
                      addClasses="edit-icon"
                      style={{ color: "#235AE4" }}
                      size={20}
                    />
                    <span className="edit-text">
                      {editingUsers ? "Done" : "Edit"}
                    </span>
                  </div>
                </div>
                <UserReviewContainer>
                  {eligibleCompanies === 0 ? (
                    <p>No companies details.</p>
                  ) : editingUsers ? (
                    <p>Editing companies and users...</p>
                  ) : (
                    <RenderInsights columnHeadings={cmpuserHeadings} dataRows={cmpuserdataRows} />
                  )}
                </UserReviewContainer>
              </UserMainReviewCard>
              
              <UserMainReviewCard>
                <div className="edit-comp">
                  <USBText as="h2">Campaign Schedule</USBText>
                </div>
                <UserReviewContainer>
                  <div className="schedule-details">
                    <div className="schedule-item">
                      <span className="label">Campaign Name:</span>
                      <span className="value">{campaignName || `Campaign for ${selectedBanners[0] || 'Unnamed'}`}</span>
                    </div>
                    <div className="schedule-item">
                      <span className="label">Start Date:</span>
                      <span className="value">{startDate}</span>
                    </div>
                    <div className="schedule-item">
                      <span className="label">Duration:</span>
                      <span className="value">{selectedDuration} months</span>
                    </div>
                    <div className="schedule-item">
                      <span className="label">Display Frequency:</span>
                      <span className="value">{selectedFrequency} times per week</span>
                    </div>
                    <div className="schedule-item">
                      <span className="label">Display Capping:</span>
                      <span className="value">{selectedCapping}</span>
                    </div>
                    <div className="schedule-item">
                      <span className="label">Display Location:</span>
                      <span className="value">Corporate Connect dashboard</span>
                    </div>
                  </div>
                </UserReviewContainer>
              </UserMainReviewCard>
              
              <ButtonRow style={{ width: "928px" }}>
                <USBButton
                  emphasis="minimal"
                  ctaStyle="standard"
                  handleClick={() => setCurrentStep(0)}
                >
                  Cancel
                </USBButton>
                <USBButton
                  emphasis="subtle"
                  ctaStyle="standard"
                  handleClick={() => setCurrentStep(2)}
                >
                  Back
                </USBButton>
                <USBButton
                  emphasis="heavy"
                  ctaStyle="standard"
                  handleClick={handleConfirm}
                  disabled={!selectedInsightType || selectedBanners.length === 0}
                >
                  Submit
                </USBButton>
              </ButtonRow>
            </ReviewMainCard>
          )}
        </ContentWrapper>
      </PageContainer>

      <USBModal
        isOpen={isVersionModalOpen}
        handleClose={handleModalClose}
      >
        <ModalHeader id="one">
          {modalSuccess ? "Campaign Scheduled" : "Submission Failed"}
        </ModalHeader>
        <ModalBody>
          <p>{modalMessage}</p>
        </ModalBody>
        <ModalFooter>
          <USBButton
            emphasis="heavy"
            ctaStyle="standard"
            handleClick={handleModalClose}
          >
            {modalSuccess ? "Done" : "Close"}
          </USBButton>
        </ModalFooter>
      </USBModal>
    </React.Fragment>
  );
};

export default ManageCampaign;