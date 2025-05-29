# Session based user level campaign rotation: Complete Implementation Guide

## Database Schema

```sql
-- New table for session tracking
CREATE TABLE user_session_tracker (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    company_id VARCHAR(255) NOT NULL,
    campaign_id VARCHAR(36) NOT NULL,
    viewed_in_session BOOLEAN DEFAULT FALSE,
    session_start_time DATETIME NOT NULL,
    last_activity_time DATETIME NOT NULL,
    session_active BOOLEAN DEFAULT TRUE,
    week_start_date DATETIME NOT NULL,
    
    INDEX idx_session_user_campaign (session_id, user_id, company_id, campaign_id),
    INDEX idx_session_active (session_id, session_active)
);

-- New table for user preferences (Jira story)
CREATE TABLE user_insight_preferences (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    company_id VARCHAR(255) NOT NULL,
    campaign_id VARCHAR(36), -- NULL for global preferences
    preference_type VARCHAR(50) NOT NULL,
    closure_count INT DEFAULT 0,
    opt_out_reason TEXT,
    opt_out_date DATETIME,
    next_eligible_date DATETIME,
    created_date DATETIME NOT NULL,
    updated_date DATETIME,
    
    INDEX idx_user_company (user_id, company_id),
    INDEX idx_user_company_campaign (user_id, company_id, campaign_id),
    INDEX idx_cooling_period (next_eligible_date, preference_type)
);
```

## API Endpoints Usage Guide

### 1. Main Campaign Retrieval

#### **GET /api/v1/rotatecampaign/next**

**When to call:** Every time user visits the dashboard/page where campaigns should be displayed

**Parameters:**
- `date`: Current date in yyyyMMdd format (e.g., "20240101")
- `company`: Company identifier
- `userId`: User identifier

**Usage Flow:**
```javascript
// Call when user loads dashboard
GET /api/v1/rotatecampaign/next?date=20240101&company=ABC123&userId=john.doe

// Response on first call in session:
{
  "id": "campaign-uuid-123",
  "name": "Q4 Savings Campaign",
  "alreadyViewedInSession": false,
  "sessionId": "session-uuid-456",
  "displayCapping": 9,        // Remaining uses
  "frequencyPerWeek": 4,      // Remaining this week
  "bannerId": "banner-123",
  // ... other campaign fields
}

// Response on subsequent calls in SAME session (page refresh):
{
  "id": "campaign-uuid-123",
  "name": "Q4 Savings Campaign", 
  "alreadyViewedInSession": true,  // Now true
  "sessionId": "session-uuid-456", // Same session
  "displayCapping": 8,             // Shows reduced capacity
  "frequencyPerWeek": 3,           // Shows what it will be after session ends
  // ... same campaign data
}
```

**Key Points:**
- User can refresh page unlimited times - same campaign returned
- Database counters only reduced when session ends
- `alreadyViewedInSession` tells you if this is a repeat view in session

### 2. Insight Closure Handling

#### **POST /api/v1/rotatecampaign/close-insight**

**When to call:** When user clicks "X" or "Close" button on campaign

**Request Body:**
```json
{
  "userId": "john.doe",
  "companyId": "ABC123", 
  "campaignId": "campaign-uuid-123"
}
```

**Response Examples:**

**First Closure:**
```json
{
  "userId": "john.doe",
  "campaignId": "campaign-uuid-123",
  "action": "HIDE_UNTIL_NEXT_ELIGIBLE",
  "message": "This insight will be hidden until you're next eligible to see it.",
  "showDialog": false
}
```

**Second Closure:**
```json
{
  "userId": "john.doe", 
  "campaignId": "campaign-uuid-123",
  "action": "ASK_SEE_AGAIN",
  "message": "Do you want to see this type of insight again?",
  "showDialog": true
}
```

**Third+ Closure:**
```json
{
  "userId": "john.doe",
  "campaignId": "campaign-uuid-123", 
  "action": "ASK_STOP_ALL",
  "message": "Would you like to stop seeing all insights?",
  "showDialog": true
}
```

### 3. User Response to Dialogs

#### **POST /api/v1/rotatecampaign/see-again-response**

**When to call:** After user responds to "Do you want to see this insight again?" dialog

**Request Body:**
```json
{
  "userId": "john.doe",
  "companyId": "ABC123",
  "campaignId": "campaign-uuid-123", 
  "wantToSeeAgain": false,
  "reason": "Not relevant to my role"
}
```

**Response if wantToSeeAgain=false:**
```json
{
  "userId": "john.doe",
  "campaignId": "campaign-uuid-123",
  "action": "ONE_MONTH_WAIT",
  "message": "You won't see this insight for at least one month."
}
```

#### **POST /api/v1/rotatecampaign/stop-all-response**

**When to call:** After user responds to "Do you want to stop all insights?" dialog

**Request Body:**
```json
{
  "userId": "john.doe",
  "companyId": "ABC123",
  "stopAll": true,
  "reason": "Too many notifications"
}
```

**Response if stopAll=true:**
```json
{
  "userId": "john.doe",
  "action": "OPTED_OUT_ALL", 
  "message": "You have been removed from all insight displays."
}
```

### 4. Session Management

#### **POST /api/v1/rotatecampaign/end-session**

**When to call:** 
- When user explicitly logs out
- Before user session timeout (optional)
- For testing/debugging purposes

**Usage:**
```javascript
POST /api/v1/rotatecampaign/end-session

// Response:
"Session ended successfully - views applied to database"
```

**What happens:**
- All campaigns viewed in the session get their counters reduced by 1
- Session trackers are marked as inactive
- Database is updated with final view counts

#### **GET /api/v1/rotatecampaign/session-stats**

**When to call:** For debugging or monitoring session state

**Parameters:**
- `userId`: User identifier  
- `company`: Company identifier

**Response:**
```json
{
  "sessionId": "session-uuid-456",
  "userId": "john.doe", 
  "companyId": "ABC123",
  "userEligible": true,
  "preferenceSummary": "User is eligible for campaigns",
  "activeCampaignsCount": 1
}
```

## Frontend Implementation Flow

### Dashboard Page Load
```javascript
// 1. User visits dashboard
async function loadDashboard() {
  try {
    const response = await fetch('/api/v1/rotatecampaign/next?date=20240101&company=ABC123&userId=john.doe');
    const campaign = await response.json();
    
    if (campaign.id) {
      // Display campaign banner
      displayCampaign(campaign);
      
      // Add close button handler
      document.getElementById('close-btn').onclick = () => closeCampaign(campaign);
    }
  } catch (error) {
    console.log('No campaign available:', error.message);
  }
}

// 2. User closes campaign
async function closeCampaign(campaign) {
  const response = await fetch('/api/v1/rotatecampaign/close-insight', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId: 'john.doe',
      companyId: 'ABC123', 
      campaignId: campaign.id
    })
  });
  
  const result = await response.json();
  
  if (result.showDialog) {
    if (result.action === 'ASK_SEE_AGAIN') {
      showSeeAgainDialog(campaign, result.message);
    } else if (result.action === 'ASK_STOP_ALL') {
      showStopAllDialog(result.message);
    }
  } else {
    // Just hide the campaign
    hideCampaign();
  }
}

// 3. Handle dialog responses
async function handleSeeAgainResponse(campaign, wantToSee, reason = '') {
  await fetch('/api/v1/rotatecampaign/see-again-response', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId: 'john.doe',
      companyId: 'ABC123',
      campaignId: campaign.id,
      wantToSeeAgain: wantToSee,
      reason: reason
    })
  });
  
  hideCampaign();
}

async function handleStopAllResponse(stopAll, reason = '') {
  await fetch('/api/v1/rotatecampaign/stop-all-response', {
    method: 'POST', 
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId: 'john.doe',
      companyId: 'ABC123',
      stopAll: stopAll,
      reason: reason
    })
  });
  
  hideCampaign();
}

// 4. Handle logout (optional - session will auto-end on timeout)
async function handleLogout() {
  await fetch('/api/v1/rotatecampaign/end-session', { method: 'POST' });
  // Continue with normal logout process
}
```

## Key Behavioral Rules

### Session Logic
1. **First API call in session**: Campaign marked as viewed, counters will be reduced when session ends
2. **Page refresh/navigation in same session**: Same campaign returned, no additional tracking
3. **Session timeout/logout**: Database updated with -1 to frequencyPerWeek and displayCapping
4. **New session**: Treated as potentially new view (subject to weekly/capping limits)

### User Preference Logic  
1. **1st closure**: Hide until user is next eligible (next week if weekly frequency allows)
2. **2nd closure**: Ask "See this again?" - if NO, 1-month cooling period for this campaign
3. **3rd+ closure**: Ask "Stop all insights?" - if YES, permanent opt-out; if NO, 1-month global cooling

### Eligibility Checks (in order)
1. User opted out of all campaigns? → Block
2. User in global cooling period? → Block  
3. User opted out of specific campaign? → Block
4. User in cooling period for specific campaign? → Block
5. Campaign within date range and active? → Allow
6. User enrolled in campaign? → Allow
7. Weekly frequency remaining? → Allow
8. Display cap remaining? → Allow

## Error Handling

### Common Error Responses
```json
// User not eligible
{
  "error": "User has opted out of all campaigns"
}

// No campaigns available  
{
  "error": "No eligible campaigns found for the company on the requested date"
}

// Session limit (shouldn't happen with this logic, but for safety)
{
  "error": "No more views available for this week"  
}
```

### Frontend Error Handling
```javascript
async function loadDashboard() {
  try {
    const response = await fetch('/api/v1/rotatecampaign/next?...');
    
    if (!response.ok) {
      const error = await response.json();
      if (error.error.includes('opted out')) {
        // User has opted out - don't show anything
        return;
      } else if (error.error.includes('No eligible campaigns')) {
        // No campaigns available - normal case
        return;
      }
    }
    
    const campaign = await response.json();
    displayCampaign(campaign);
    
  } catch (error) {
    console.log('Campaign loading failed:', error);
  }
}
```

## Configuration Required

### application.yml
```yaml
server:
  servlet:
    session:
      timeout: 30m  # Session timeout
      
spring:
  jpa:
    hibernate:
      ddl-auto: update  # To create new tables
```


This implementation provides a complete session-based campaign management system with progressive user preference handling, exactly matching your requirements of "one view per session" regardless of page refreshes.