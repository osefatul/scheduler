# Campaign Rotation System Testing Guide
This README provides a comprehensive guide for testing the Campaign Rotation System from initial setup through all key scenarios.
Table of Contents

Environment Setup
Database Setup
Campaign Creation Tests
Basic Rotation Tests
Frequency & Capping Tests
Edge Case Tests
Company Management Tests
Automated Testing

1. Environment Setup
Set up your testing environment with these variables:
bashCopy# Base environment variables
BASE_URL="http://localhost:8080"
CONTENT_TYPE="Content-Type: application/json"
2. Database Setup
Execute these SQL scripts to prepare your database:
```sql
-- Create campaign_company_mapping table
CREATE TABLE campaign_company_mapping (
    id VARCHAR(36) PRIMARY KEY,
    campaign_id VARCHAR(36) NOT NULL,
    company_id VARCHAR(100) NOT NULL,
    CONSTRAINT fk_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns_dev_rotation1(id) ON DELETE CASCADE,
    CONSTRAINT uk_campaign_company UNIQUE (campaign_id, company_id)
);

-- Create indexes
CREATE INDEX idx_campaign_id ON campaign_company_mapping (campaign_id);
CREATE INDEX idx_company_id ON campaign_company_mapping (company_id);

-- Insert test companies
INSERT INTO companies (id, name, status) VALUES
('ABCCorp', 'ABC Corporation', 'ACTIVE'),
('XYZInc', 'XYZ Inc.', 'ACTIVE'),
('123Corp', '123 Corporation', 'ACTIVE');

```
3. Campaign Creation Tests
First Campaign (Complete All Steps)
```bash
# Step 1: Setup

CAMPAIGN1_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/campaigns" \
  -H "$CONTENT_TYPE" \
  -d '{
    "name": "First Campaign",
    "bannerId": "BANNER-001",
    "insightType": "PROMOTION",
    "insightSubType": "SEASONAL",
    "insight": "Spring promotion for all companies",
    "companyNames": "ABCCorp|XYZInc",
    "eligibleCompanies": 10,
    "eligibleUsers": 1000,
    "createdBy": "admin",
    "action": "SETUP"
  }')
  
CAMPAIGN1_ID=$(echo $CAMPAIGN1_RESPONSE | jq -r '.id')

# Step 2: Configure
curl -X POST "$BASE_URL/api/v1/campaigns" \
  -H "$CONTENT_TYPE" \
  -d "{
    \"id\": \"$CAMPAIGN1_ID\",
    \"startDate\": \"2025-05-01\",
    \"endDate\": \"2025-05-31\",
    \"frequencyPerWeek\": 2,
    \"displayCapping\": 8,
    \"displayLocation\": \"HOME_PAGE\",
    \"action\": \"CONFIGURE\"
  }"

# Step 3: Review
curl -X POST "$BASE_URL/api/v1/campaigns" \
  -H "$CONTENT_TYPE" \
  -d "{
    \"id\": \"$CAMPAIGN1_ID\",
    \"action\": \"REVIEW\"
  }"

# Step 4: Submit
curl -X POST "$BASE_URL/api/v1/campaigns" \
  -H "$CONTENT_TYPE" \
  -d "{
    \"id\": \"$CAMPAIGN1_ID\",
    \"action\": \"SUBMIT\"
  }"

# Verify Status
curl -X GET "$BASE_URL/api/v1/campaigns/$CAMPAIGN1_ID" \
  -H "$CONTENT_TYPE"

```
## Create Additional Campaigns
Create at least two more complete campaigns following the same pattern, adjusting:

- Name/BannerID
- Company associations
- Dates if desired

Also create one incomplete campaign that only completes "SETUP" and "CONFIGURE" steps to remain in "DRAFT" status.
4. Basic Rotation Tests
Test rotation across different weeks and companies:
Week 19 (May 5-11, 2025)
```bash
# ABCCorp should see Campaign 1 (oldest)
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250505&company=ABCCorp" \
  -H "$CONTENT_TYPE"

# XYZInc should also see Campaign 1
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250505&company=XYZInc" \
  -H "$CONTENT_TYPE"

# 123Corp should see Campaign 2 (Campaign 1 isn't eligible)
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250505&company=123Corp" \
  -H "$CONTENT_TYPE"
```

## Week 20 (May 12-18, 2025)
```bash
Copy# Test rotation for all companies
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250512&company=ABCCorp" \
  -H "$CONTENT_TYPE"
```
Repeat for all companies and verify the rotation pattern continues properly in Week 21, Week 22, etc.
5. Frequency & Capping Tests
## Weekly Frequency Test
```bash
Copy# First call - Should succeed
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250505&company=ABCCorp" \
  -H "$CONTENT_TYPE"

# Second call same week - Should succeed but reduce frequency
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250507&company=ABCCorp" \
  -H "$CONTENT_TYPE"

# Third call same week - Should fail or return different campaign
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250509&company=ABCCorp" \
  -H "$CONTENT_TYPE"

```
## Display Capping Test
```bash
Copy# Modify Campaign 1 to have display capping = 2
curl -X PUT "$BASE_URL/api/v1/campaigns/$CAMPAIGN1_ID" \
  -H "$CONTENT_TYPE" \
  -d "{\"displayCapping\": 2}"

# Call API in Week 19
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250505&company=ABCCorp" \
  -H "$CONTENT_TYPE"

# Call API in Week 22 - Should be the last time Campaign 1 shows
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250526&company=ABCCorp" \
  -H "$CONTENT_TYPE"

# Call API in Week 25 - Campaign 1 should not show anymore
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250616&company=ABCCorp" \
  -H "$CONTENT_TYPE"

# Verify Campaign 1 status shows "COMPLETED"
curl -X GET "$BASE_URL/api/v1/campaigns/$CAMPAIGN1_ID" \
  -H "$CONTENT_TYPE"

```
6. Edge Case Tests
## No Eligible Campaigns
```bash
Copy# Test with a date before any campaign starts
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250401&company=ABCCorp" \
  -H "$CONTENT_TYPE"

# Test with a date after all campaigns end
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250701&company=ABCCorp" \
  -H "$CONTENT_TYPE"

# Test with non-existent company
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250505&company=NonExistentCorp" \
  -H "$CONTENT_TYPE"
```

### Weekly Reset Verification
```bash
# Exhaust Campaign 2's weekly frequency
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250512&company=ABCCorp" \
  -H "$CONTENT_TYPE"
# Repeat until frequency = 0

# Verify frequency is 0
curl -X GET "$BASE_URL/api/v1/campaigns/$CAMPAIGN2_ID" \
  -H "$CONTENT_TYPE"

# Test after weekend (next Monday)
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250519&company=ABCCorp" \
  -H "$CONTENT_TYPE"

# Verify frequency was reset
curl -X GET "$BASE_URL/api/v1/campaigns/$CAMPAIGN2_ID" \
  -H "$CONTENT_TYPE"
```
7. Company Management Tests
## Update Campaign Companies
```bash
# Update Campaign 2 to add XYZInc
curl -X PUT "$BASE_URL/api/v1/campaigns/$CAMPAIGN2_ID/companies?companyNames=ABCCorp|XYZInc|123Corp" \
  -H "$CONTENT_TYPE"

# Verify XYZInc now gets proper rotation for Campaign 2
curl -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=20250512&company=XYZInc" \
  -H "$CONTENT_TYPE"
```

8. Automated Testing

Use this script to automate the testing process:
```bash
#!/bin/bash

# Variables
BASE_URL="http://localhost:8082"
CONTENT_TYPE="Content-Type: application/json"

# Functions
function create_campaign() {
  local name=$1
  local banner_id=$2
  local companies=$3
  
  echo "Creating campaign: $name..."
  
  # Step 1: Setup
  local response=$(curl -s -X POST "$BASE_URL/api/v1/campaigns" \
    -H "$CONTENT_TYPE" \
    -d "{
      \"name\": \"$name\",
      \"bannerId\": \"$banner_id\",
      \"insightType\": \"PROMOTION\",
      \"insightSubType\": \"SEASONAL\",
      \"insight\": \"Test campaign\",
      \"companyNames\": \"$companies\",
      \"eligibleCompanies\": 10,
      \"eligibleUsers\": 1000,
      \"createdBy\": \"admin\",
      \"action\": \"SETUP\"
    }")
  
  local id=$(echo $response | jq -r '.id')
  
  # Step 2: Configure
  curl -s -X POST "$BASE_URL/api/v1/campaigns" \
    -H "$CONTENT_TYPE" \
    -d "{
      \"id\": \"$id\",
      \"startDate\": \"2025-05-01\",
      \"endDate\": \"2025-05-31\",
      \"frequencyPerWeek\": 2,
      \"displayCapping\": 8,
      \"displayLocation\": \"HOME_PAGE\",
      \"action\": \"CONFIGURE\"
    }"
  
  # Step 3: Review
  curl -s -X POST "$BASE_URL/api/v1/campaigns" \
    -H "$CONTENT_TYPE" \
    -d "{
      \"id\": \"$id\",
      \"action\": \"REVIEW\"
    }"
  
  # Step 4: Submit
  curl -s -X POST "$BASE_URL/api/v1/campaigns" \
    -H "$CONTENT_TYPE" \
    -d "{
      \"id\": \"$id\",
      \"action\": \"SUBMIT\"
    }"
  
  echo "Campaign created with ID: $id"
  return $id
}

function test_rotation() {
  local date=$1
  local company=$2
  
  echo "Testing rotation for date: $date, company: $company"
  curl -s -X GET "$BASE_URL/api/v1/rotatecampaign/next?date=$date&company=$company" \
    -H "$CONTENT_TYPE" | jq
}

# Main test flow
echo "Starting automated test..."

# Create test campaigns
create_campaign "First Campaign" "BANNER-001" "ABCCorp|XYZInc"
create_campaign "Second Campaign" "BANNER-002" "ABCCorp|123Corp"
create_campaign "Third Campaign" "BANNER-003" "ABCCorp|XYZInc|123Corp"

# Test rotation in different weeks
echo "Testing Week 19 rotation..."
test_rotation "20250505" "ABCCorp"
test_rotation "20250505" "XYZInc"
test_rotation "20250505" "123Corp"

echo "Testing Week 20 rotation..."
test_rotation "20250512" "ABCCorp"
test_rotation "20250512" "XYZInc"
test_rotation "20250512" "123Corp"

echo "Testing Week 21 rotation..."
test_rotation "20250519" "ABCCorp"
test_rotation "20250519" "XYZInc"
test_rotation "20250519" "123Corp"

echo "Test completed successfully."


```

## Expected Test Results
### Campaign Creation

Completed campaigns show status="INPROGRESS" and campaignSteps="SUBMIT"
Incomplete campaign shows status="DRAFT"

### Rotation Pattern

Week 19:

ABCCorp, XYZInc: See Campaign 1 (oldest)
123Corp: See Campaign 2 (oldest eligible)


Week 20:

ABCCorp, 123Corp: See Campaign 2
XYZInc: See Campaign 3 (Campaign 2 not eligible)


Week 21: All companies see Campaign 3
Week 22: Cycles back to Campaign 1

Frequency Tests

After two API calls in same week, campaign frequency should be 0
Third call should return a different campaign or error
After week boundary, frequency should reset to original value

Display Capping

After reaching display capping, campaign visibility should be "COMPLETED"
Completed campaigns shouldn't appear in rotation again