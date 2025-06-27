import USBButton from "@usb-shield/react-button";
import {
  Breadcrumb,
  ButtonContainer,
  HeroImage,
  VideoPlayer,
  ContentSection,
  MediaSection,
  PageContainer,
  HeroSectionContainer,
  VideoSectionContainer,
  KeyBenefitSectionContainer,
  SectionMainCard,
  SectionLiData,
  StyledHeadingH1,
  StyledHeadingH2,
  StyledParagraph,
  SectionHeading,
  ListItem,
  IconRow,
  ContentRow,
  Heading,
  Description,
  LetsTalkSectionContainer,
  BreadCumbbackspan,
  VedioText,
  // VideoWrapper,
  // VideoContainer,
} from "./LearnMore.styled";
import { USBIconArrowLeft } from "@usb-shield/react-icons";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useGetLearnMoreContentByProductsQuery } from "@/external/services/learnMoreAPI";
import { Spinner } from "../spinner";
import LearnMoreModal from "../modal/LearnMoreModal";
import { useEffect, useState } from "react";
import { STATIC_CONTENT } from "./LearnMoreStaticContent";
import {
  createHotLeadSafely,
  HotLeadData,
  trackWarmLeadSafely,
  useCreateLeadMutation,
  useTrackWarmLeadMutation,
  WarmLeadData,
} from "@/external/services/LeadAPI";
import { useCampaignSessionManager } from "@/external/utils/campaignSessionManager";

const LearnMorePage = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [isMarketoFormSubmitted, setIsMarketoFormSubmitted] = useState(false);
  const [warmLeadTracked, setWarmLeadTracked] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const searchParams = new URLSearchParams(location.search);
  const productName = searchParams.get("productname");
  const campaignId = searchParams.get("campaignId");
  const userId = searchParams.get("userId");
  const companyId = searchParams.get("companyId");

  const { clearCampaignData } = useCampaignSessionManager();

  const insightSubType = productName
    ? productName
    : (location.state as { insightSubType?: keyof typeof STATIC_CONTENT })
        ?.insightSubType || "FX";
  const content =
    STATIC_CONTENT[insightSubType as keyof typeof STATIC_CONTENT] ||
    STATIC_CONTENT.VirtualPay;

  let dcr_isLearnMoreContentDynamic = true;
  const { data, isLoading } = useGetLearnMoreContentByProductsQuery(
    insightSubType.replace(/\s+/g, "").toLocaleLowerCase(),
    {
      skip: !dcr_isLearnMoreContentDynamic,
    }
  );
  const [createLead] = useCreateLeadMutation();
  const [trackWarmLead] = useTrackWarmLeadMutation();

  useEffect(() => {
    const trackPageVisit = async () => {
      if (!campaignId || warmLeadTracked) {
        return;
      }

      try {
        const warmLeadData: WarmLeadData = {
          userId,
          campaignId,
          insightSubType,
        };

        const success = await trackWarmLeadSafely(trackWarmLead, warmLeadData);

        if (success) {
          setWarmLeadTracked(true);
        }
      } catch (error) {
        console.error("Error tracking warm lead:", error);
      }
    };

    const timer = setTimeout(trackPageVisit, 1000);
    return () => clearTimeout(timer);
  }, [campaignId, insightSubType, warmLeadTracked, trackWarmLead]);

  if (dcr_isLearnMoreContentDynamic && (!data || data?.data?.length === 0)) {
    dcr_isLearnMoreContentDynamic = false;
  }

  if (isLoading || isMarketoFormSubmitted) {
    return <Spinner />;
  }

  if (dcr_isLearnMoreContentDynamic && !data) {
    return null;
  }

  const handleClose = () => {
    setIsOpen(false);
    window.location.href = "/";
  };

  const handleTalkToRM = async () => {
    setIsMarketoFormSubmitted(true);

    const sampleData = {
      FirstName: "John",
      LastName: "Doe",
      Email: "john.doe@usbank.com",
      Company: companyId,
      Phone: "123-456-7890",
      corporateConnectInsight: insightSubType,
    };

    const leadData: HotLeadData = {
      leadId: "",
      campaignId: campaignId,
      firstName: sampleData.FirstName,
      lastName: sampleData.LastName,
      emailAddress: sampleData.Email,
      companyName: sampleData.Company ?? "",
      phoneNumber: sampleData.Phone,
      comments: "Testing comments",
      marketingRelationshipType: "USBank",
      corporateConnectInsight: insightSubType,
      userId,
    };

    const script = document.createElement("script");
    script.src = "https://uat-engage.usbank.com/js/forms2/js/forms2.min.js";
    script.async = true;
    script.onload = () => {
      // @ts-ignore: MktoForms2 is a global variable provided by the Marketo script
      MktoForms2.loadForm(
        "https://uat-engage.usbank.com",
        "866-CNF-351",
        1872,
        (form: any) => {
          form.vals(sampleData);

          form.onSuccess((_values: any, followUpUrl: string) => {
            const urlParams = new URLSearchParams(new URL(followUpUrl).search);
            const extractedID = urlParams.get("aliId");
            const leadId = extractedID || null;

            if (!leadId) {
              console.error("leadId not found in form values");
            } else {
              leadData.leadId = leadId;
              handleHotLeadCreation(leadData);
            }
            return false;
          });
          form.submit();
        }
      );
    };
    document.body.appendChild(script);
  };

  const handleHotLeadCreation = async (leadData: HotLeadData) => {
    if (!leadData.campaignId || !leadData.leadId) {
      return;
    }

    try {
      const response = await createHotLeadSafely(createLead, leadData);

      if (response) {
        clearCampaignData();
        setIsOpen(true);
      } else {
        console.error("Failed to create hot lead");
      }
    } catch (error) {
      console.error("Error creating hot lead:", error);
    } finally {
      setIsMarketoFormSubmitted(false);
    }
  };

  const renderHeroSection = () => {
    if (dcr_isLearnMoreContentDynamic && data?.data?.[0]?.heroSection) {
      const { heading, subHeading, paragrpah, image } =
        data?.data?.[0]?.heroSection;
      return (
        <SectionMainCard>
          <HeroSectionContainer>
            <ContentSection>
              <StyledHeadingH2 className="capsHeading">
                {heading}
              </StyledHeadingH2>
              <StyledHeadingH2>{subHeading}</StyledHeadingH2>
              <StyledParagraph>{paragrpah}</StyledParagraph>
              <ButtonContainer>
                <USBButton
                  emphasis="heavy"
                  ctaStyle="standard"
                  handleClick={handleTalkToRM}
                >
                  Talk to an RM
                </USBButton>
              </ButtonContainer>
            </ContentSection>
            <MediaSection>
              <HeroImage src={image} alt="Hero Banner" />
            </MediaSection>
          </HeroSectionContainer>
        </SectionMainCard>
      );
    }
    if (!dcr_isLearnMoreContentDynamic) {
      const { heading, subHeading, paragraph, image } = content.heroSection;
      return (
        <SectionMainCard>
          <HeroSectionContainer>
            <ContentSection>
              <StyledHeadingH2 className="capsHeading">
                {heading}
              </StyledHeadingH2>
              <StyledHeadingH2>{subHeading}</StyledHeadingH2>
              <StyledParagraph>{paragraph}</StyledParagraph>
              <ButtonContainer>
                <USBButton
                  emphasis="heavy"
                  ctaStyle="standard"
                  handleClick={handleTalkToRM}
                >
                  Talk to an RM
                </USBButton>
              </ButtonContainer>
            </ContentSection>
            <MediaSection>
              <HeroImage src={image} alt="Hero Banner" />
            </MediaSection>
          </HeroSectionContainer>
        </SectionMainCard>
      );
    }
    return null;
  };

  const renderMediaSection = () => {
    if (dcr_isLearnMoreContentDynamic && data?.data?.[0]?.mediaSection) {
      const { heading, paragrpah, image } = data?.data?.[0]?.mediaSection;
      return (
        <SectionMainCard>
          <VideoSectionContainer>
            <MediaSection>
              <VideoPlayer poster={image} controls>
                <source src="" type="video/mp4" />
                Your browser does not support the video tag.
              </VideoPlayer>
              <VedioText>
                <Link to="/transcript"> View transcript </Link>
              </VedioText>
            </MediaSection>
            <ContentSection>
              <StyledHeadingH2>{heading}</StyledHeadingH2>
              <StyledParagraph>{paragrpah}</StyledParagraph>
            </ContentSection>
          </VideoSectionContainer>
        </SectionMainCard>
      );
    }
    if (!dcr_isLearnMoreContentDynamic) {
      const { heading, paragraph, videoPoster } = content.mediaSection;

      return (
        <SectionMainCard>
          <VideoSectionContainer>
            <MediaSection>
              {/* <VideoWrapper>
              <VideoContainer>
                <iframe
                  src="https://players.brightcove.net/4924632599001/default_default/index.html"
                  sandbox="allow-scripts allow-same-origin allow-popups"
                  width="640"
                  height="360"
                ></iframe>
              </VideoContainer>
            </VideoWrapper> */}
              <VideoPlayer poster={videoPoster} controls>
                <source src="" type="video/mp4" />
                Your browser does not support the video tag.
              </VideoPlayer>
              <VedioText>View transcript</VedioText>
            </MediaSection>
            <ContentSection>
              <StyledHeadingH2>{heading}</StyledHeadingH2>
              <StyledParagraph>{paragraph}</StyledParagraph>
            </ContentSection>
          </VideoSectionContainer>
        </SectionMainCard>
      );
    }
    return null;
  };

  const renderKeyBenefitsSection = () => {
    if (dcr_isLearnMoreContentDynamic && data?.data?.[0]?.keyBenefitSection) {
      const { heading, subHeading, keyBenefits } =
        data?.data?.[0]?.keyBenefitSection;
      return (
        <SectionMainCard>
          <KeyBenefitSectionContainer>
            <SectionHeading>
              <StyledHeadingH2>{heading}</StyledHeadingH2>
              <StyledParagraph>{subHeading}</StyledParagraph>
            </SectionHeading>
            <SectionLiData>
              {(keyBenefits ?? []).map((benefit: any, index: number) => (
                <ListItem key={index}>
                  <IconRow>
                    <img
                      src={benefit.image}
                      alt={benefit.heading}
                      className="icon"
                    />
                  </IconRow>
                  <ContentRow>
                    <Heading>{benefit.heading}</Heading>
                    <Description>{benefit.subHeading}</Description>
                  </ContentRow>
                </ListItem>
              ))}
            </SectionLiData>
          </KeyBenefitSectionContainer>
        </SectionMainCard>
      );
    }

    // Static content fallback
    if (!dcr_isLearnMoreContentDynamic) {
      const { heading, subHeading, benefits } = content.keyBenefitsSection;
      return (
        <SectionMainCard>
          <KeyBenefitSectionContainer>
            <SectionHeading>
              <StyledHeadingH2>{heading}</StyledHeadingH2>
              <StyledParagraph>{subHeading}</StyledParagraph>
            </SectionHeading>
            <SectionLiData>
              {(benefits ?? []).map((benefit: any, index: number) => (
                <ListItem key={index}>
                  <IconRow>{benefit.icon}</IconRow>
                  <ContentRow>
                    <Heading>{benefit.heading}</Heading>
                    <Description>{benefit.description}</Description>
                  </ContentRow>
                </ListItem>
              ))}
            </SectionLiData>
          </KeyBenefitSectionContainer>
        </SectionMainCard>
      );
    }
    return null;
  };

  const renderLetsTalkSection = () => (
    <SectionMainCard className="letsTalkSection">
      <LetsTalkSectionContainer>
        <SectionHeading>
          <StyledHeadingH2>Let’s talk.</StyledHeadingH2>
          <StyledParagraph>
            Interested in learning more about embedded payments?
          </StyledParagraph>
        </SectionHeading>
        <ButtonContainer className="letsTalkButton">
          <USBButton
            emphasis="heavy"
            ctaStyle="standard"
            handleClick={handleTalkToRM}
          >
            Talk to an RM
          </USBButton>
        </ButtonContainer>
      </LetsTalkSectionContainer>
    </SectionMainCard>
  );

  return (
    <PageContainer>
      {/* Breadcrumb */}
      <Breadcrumb onClick={() => navigate("/")}>
        <USBIconArrowLeft addClasses="icon" colorVariant="brand-primary" />
        <BreadCumbbackspan>Back to dashboard</BreadCumbbackspan>
      </Breadcrumb>

      <StyledHeadingH1 as="h1" style={{ margin: "1rem 0" }}>
        Learn more about embedded payments
      </StyledHeadingH1>

      {/* Render Sections */}
      {renderHeroSection()}
      {renderMediaSection()}
      {renderKeyBenefitsSection()}
      {renderLetsTalkSection()}

      <LearnMoreModal
        isOpen={isOpen}
        handleClose={handleClose}
        insightSubType={insightSubType}
      />
    </PageContainer>
  );
};

export default LearnMorePage;
