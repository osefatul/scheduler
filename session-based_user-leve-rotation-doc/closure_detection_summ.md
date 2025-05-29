# Complete Closure Detection Flow

## How Backend Detects Closure State

### **Database Tracking**
The backend tracks closure state in the `user_insight_preferences` table:

```sql
-- Each record tracks user's interaction with a specific campaign
user_id: "john.doe"
company_id: "ABC123" 
campaign_id: "campaign-123"
closure_count: 2                    -- HOW MANY TIMES USER CLOSED THIS CAMPAIGN
preference_type: "CLOSED_TWICE"     -- CURRENT STATE
opt_out_reason: "Not relevant"      -- WHY THEY OPTED OUT (if applicable)
next_eligible_date: "2024-02-01"    -- WHEN THEY CAN SEE IT AGAIN (if in cooling period)
```

### **Detection Logic in handleInsightClosure()**

```java
// When user clicks close button, backend:

1. Look up existing preference record for this user + campaign
2. If found: increment closure_count, if not found: create new with count = 1
3. Based on NEW closure count, determine action:

   closure_count == 1:  → "HIDE_UNTIL_NEXT_ELIGIBLE" (no modal)
   closure_count == 2:  → "ASK_SEE_AGAIN" (show modal)
   closure_count >= 3:  → "ASK_STOP_ALL" (show modal)
```

## Complete Flow Examples

### **Scenario 1: New User, New Campaign**

```
Database State: No record exists
User Action: Clicks close (X) button
Backend Logic:
  - No existing record found
  - Create new record with closure_count = 1
  - Set preference_type = "CLOSED_ONCE"
Response:
  {
    "action": "HIDE_UNTIL_NEXT_ELIGIBLE",
    "showDialog": false,
    "message": "This insight will be hidden until you're next eligible to see it.",
    "closureCount": 1
  }
Frontend Action: Just hide banner, show message
```

### **Scenario 2: User Sees Campaign Again, Closes Second Time**

```
Database State: closure_count = 1, preference_type = "CLOSED_ONCE"
User Action: Clicks close (X) button again
Backend Logic:
  - Found existing record with count = 1
  - Increment to closure_count = 2
  - Set preference_type = "CLOSED_TWICE"
Response:
  {
    "action": "ASK_SEE_AGAIN", 
    "showDialog": true,
    "dialogType": "SEE_AGAIN",
    "message": "Do you want to see this type of insight again?",
    "closureCount": 2
  }
Frontend Action: Show "See Again?" modal with Yes/No options
```

### **Scenario 3: User Closes Third Time**

```
Database State: closure_count = 2, preference_type = "CLOSED_TWICE" 
User Action: Clicks close (X) button third time
Backend Logic:
  - Found existing record with count = 2
  - Increment to closure_count = 3
Response:
  {
    "action": "ASK_STOP_ALL",
    "showDialog": true, 
    "dialogType": "STOP_ALL",
    "message": "Would you like to stop seeing all insights?",
    "closureCount": 3
  }
Frontend Action: Show "Stop All?" modal with options
```

## Key Detection Points

### **1. GET /next Endpoint (Campaign Retrieval)**
```java
// BEFORE serving any campaign, backend checks:
boolean eligible = preferenceService.isUserEligibleForCampaign(userId, companyId, campaignId);

// This method checks:
- Is user in cooling period for this campaign?
- Has user opted out of this specific campaign?
- Has user opted out of ALL campaigns?

// If ANY of these are true → campaign is filtered out
// User will get "No campaigns available" instead
```

### **2. Campaign Response Includes Closure Info**
```json
GET /api/v1/rotatecampaign/next response:
{
  "id": "campaign-123",
  "name": "Q4 Savings Campaign",
  "closureCount": 1,                    // HOW MANY TIMES USER CLOSED THIS BEFORE
  "nextClosureAction": "ASK_SEE_AGAIN", // WHAT HAPPENS IF THEY CLOSE AGAIN
  "alreadyViewedInSession": false,
  // ... other fields
}
```

### **3. Debug Endpoint for Detailed State**
```javascript
GET /api/v1/rotatecampaign/campaign-interaction-info?userId=john.doe&company=ABC123&campaignId=campaign-123

Response:
{
  "closureCount": 2,
  "preferenceType": "CLOSED_TWICE", 
  "inCoolingPeriod": false,
  "nextEligibleDate": null,
  "nextClosureAction": "ASK_STOP_ALL",
  "eligible": true,
  "optOutReason": null
}
```

## Frontend Usage Patterns

### **Simple Usage (Recommended)**
```javascript
// 1. Load campaign
const campaign = await fetch('/api/v1/rotatecampaign/next?...');

// 2. Show campaign if available
if (campaign.id) {
  displayCampaign(campaign);
}

// 3. Handle close button - backend determines what modal to show
async function closeCampaign(campaign) {
  const response = await fetch('/api/v1/rotatecampaign/close-insight', {
    method: 'POST',
    body: JSON.stringify({ userId, companyId, campaignId: campaign.id })
  });
  
  const result = await response.json();
  
  // Backend tells you exactly what to do
  if (result.showDialog) {
    if (result.dialogType === 'SEE_AGAIN') {
      showSeeAgainModal(result.message);
    } else if (result.dialogType === 'STOP_ALL') {
      showStopAllModal(result.message);
    }
  } else {
    // Just hide banner
    hideCampaign();
  }
}
```

### **Advanced Usage (With State Checking)**
```javascript
// Check campaign state before displaying
async function loadCampaignWithInfo() {
  const campaign = await fetch('/api/v1/rotatecampaign/next?...');
  
  if (campaign.id) {
    console.log(`User has closed this campaign ${campaign.closureCount} times before`);
    console.log(`Next closure will: ${campaign.nextClosureAction}`);
    
    displayCampaignWithWarning(campaign);
  }
}

function displayCampaignWithWarning(campaign) {
  let warningMessage = '';
  
  if (campaign.closureCount === 1) {
    warningMessage = 'Closing this again will ask for your preference';
  } else if (campaign.closureCount >= 2) {
    warningMessage = 'Closing this again will ask about stopping all insights';
  }
  
  // Show campaign with appropriate warning
  showCampaignBanner(campaign, warningMessage);
}
```

## Database States and Transitions

### **State Transition Diagram**
```
NO RECORD
    ↓ (first close)
CLOSED_ONCE (closure_count: 1)
    ↓ (second close)  
CLOSED_TWICE (closure_count: 2) → Ask "See again?"
    ↓ (user says NO)              ↓ (user says YES)
COOLING_PERIOD                   CLOSED_TWICE (continues normal)
    ↓ (third close)               ↓ (third close)
Ask "Stop all?"                  Ask "Stop all?"
    ↓ (user says YES)    ↓ (user says NO)
OPTED_OUT_ALL           COOLING_PERIOD (global)
```

### **Key Database Values**
```sql
-- User never interacted with campaign
No record exists → Show campaign normally

-- User closed once  
closure_count: 1, preference_type: "CLOSED_ONCE" → Show campaign, next close asks preference

-- User closed twice but no response yet
closure_count: 2, preference_type: "CLOSED_TWICE" → Show campaign, next close asks stop all

-- User said NO to "see again"
closure_count: 2, preference_type: "COOLING_PERIOD", next_eligible_date: future → Hide campaign

-- User opted out of specific campaign
preference_type: "OPTED_OUT_CAMPAIGN" → Hide this campaign forever

-- User opted out of ALL campaigns  
campaign_id: NULL, preference_type: "OPTED_OUT_ALL" → Hide all campaigns forever
```

## Error Handling

### **Missing Preference Record**
```java
// If database record is corrupted/missing:
Optional<UserInsightPreference> pref = preferenceRepository.find...();

if (!pref.isPresent()) {
    // Treat as first-time interaction
    return createNewPreference(userId, companyId, campaignId);
}
```

### **Invalid State Recovery**
```java
// If preference_type doesn't match closure_count:
if (preference.getClosureCount() == 0 && preference.getPreferenceType() != null) {
    // Reset to clean state
    preference.setPreferenceType(null);
    preference.setClosureCount(0);
}
```

This system automatically tracks and detects user closure behavior without requiring frontend to maintain state. The backend always knows exactly what modal to show based on the user's history with each specific campaign.