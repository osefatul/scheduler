# Campaign User Session & Insight Management Architecture

## Core Components Overview

### 1. User Session Management
- **Session-based tracking** instead of immediate database updates
- **Spring Boot session management** using HttpSession
- **Deferred database updates** on session completion

### 2. User Insight Preferences
- **Closure tracking** with escalating user choices
- **Feedback collection** for insight rejection
- **One-month cooling period** for rejected users
- **Progressive restriction** (insight-specific → all insights)

## New Entities

### UserInsightPreference
```java
@Entity
@Table(name = "user_insight_preferences")
public class UserInsightPreference {
    @Id
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "company_id", nullable = false)
    private String companyId;
    
    @Column(name = "campaign_id")
    private String campaignId; // null means all campaigns
    
    @Column(name = "preference_type")
    private String preferenceType; // CLOSED_ONCE, CLOSED_TWICE, OPTED_OUT
    
    @Column(name = "opt_out_reason")
    private String optOutReason;
    
    @Column(name = "opt_out_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date optOutDate;
    
    @Column(name = "next_eligible_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date nextEligibleDate;
    
    @Column(name = "created_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;
}
```

### UserSessionTracker
```java
@Entity
@Table(name = "user_session_tracker")
public class UserSessionTracker {
    @Id
    private String id;
    
    @Column(name = "session_id", nullable = false)
    private String sessionId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "company_id", nullable = false)
    private String companyId;
    
    @Column(name = "campaign_id", nullable = false)
    private String campaignId;
    
    @Column(name = "views_in_session")
    private Integer viewsInSession = 0;
    
    @Column(name = "session_start_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date sessionStartTime;
    
    @Column(name = "last_activity_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastActivityTime;
    
    @Column(name = "session_active")
    private Boolean sessionActive = true;
}
```

## Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           USER REQUESTS CAMPAIGN                                │
└─────────────────────────────┬───────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     CHECK USER INSIGHT PREFERENCES                              │
│                                                                                 │
│  • Check if user has opted out of this campaign                               │
│  • Check if user has opted out of all campaigns                               │
│  • Check if user is in cooling period (1 month wait)                          │
└─────────────────────────────┬───────────────────────────────────────────────────┘
                              │
                              ▼
                        ┌───────────┐
                        │ Eligible? │
                        └─────┬─────┘
                              │
                    ┌─────────┴─────────┐
                    │ NO              YES│
                    ▼                   ▼
    ┌─────────────────────────┐    ┌─────────────────────────┐
    │   RETURN NO CONTENT     │    │   CHECK USER SESSION   │
    │                         │    │                         │
    │ • Send appropriate      │    │ • Get/Create session    │
    │   message based on      │    │ • Check session views   │
    │   opt-out reason        │    │ • Validate limits       │
    └─────────────────────────┘    └─────────┬───────────────┘
                                             │
                                             ▼
                              ┌─────────────────────────────┐
                              │     GET NEXT CAMPAIGN       │
                              │                             │
                              │ • Apply rotation logic      │
                              │ • Check campaign eligibility│
                              │ • Apply session-based view  │
                              └─────────┬───────────────────┘
                                        │
                                        ▼
                              ┌─────────────────────────────┐
                              │    RETURN CAMPAIGN DATA     │
                              │                             │
                              │ • Include session tracking  │
                              │ • Include closure options    │
                              └─────────┬───────────────────┘
                                        │
                                        ▼
                              ┌─────────────────────────────┐
                              │     USER INTERACTIONS       │
                              └─────────┬───────────────────┘
                                        │
                        ┌───────────────┼───────────────┐
                        │               │               │
                        ▼               ▼               ▼
            ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
            │  USER VIEWS     │ │ USER CLOSES     │ │ SESSION ENDS    │
            │                 │ │                 │ │                 │
            │ • Increment     │ │ • Check closure │ │ • Update DB     │
            │   session view  │ │   count         │ │ • Apply final   │
            │ • Update last   │ │ • Show options  │ │   frequency &   │
            │   activity      │ │ • Record pref   │ │   capping       │
            └─────────────────┘ └─────┬───────────┘ └─────────────────┘
                                      │
                                      ▼
                            ┌─────────────────────────────┐
                            │    CLOSURE HANDLING         │
                            │                             │
                            │ FIRST CLOSURE:              │
                            │ • Mark as closed once       │
                            │ • Hide until next eligible  │
                            │                             │
                            │ SECOND CLOSURE:             │
                            │ • Ask: See again?           │
                            │   ├── YES: Show next time   │
                            │   └── NO: Ask reason        │
                            │        └── Set 1-month wait │
                            │                             │
                            │ SUBSEQUENT CLOSURES:        │
                            │ • Ask: No more insights?    │
                            │   ├── YES: Ask reason       │
                            │   │    └── Opt out all      │
                            │   └── NO: 1-month wait      │
                            └─────────────────────────────┘

```

## Session Management Flow

```
SESSION LIFECYCLE:
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Session Start   │───▶│ Active Session  │───▶│ Session End     │
│                 │    │                 │    │                 │
│ • Create        │    │ • Track views   │    │ • Update DB     │
│   tracker       │    │ • Monitor       │    │ • Clean up      │
│ • Initialize    │    │   activity      │    │   session data  │
│   counters      │    │ • Apply limits  │    │ • Apply final   │
└─────────────────┘    └─────────────────┘    │   calculations  │
                                              └─────────────────┘

SESSION TIMEOUT HANDLING:
┌─────────────────────────────────────────────────────────────────┐
│                    Background Job (Every 30 mins)              │
│                                                                 │
│ • Find inactive sessions (> 30 mins no activity)              │
│ • Update UserCampaignTracker with session data                │
│ • Clean up expired session trackers                           │
│ • Reset session counters                                      │
└─────────────────────────────────────────────────────────────────┘
```

## Key Implementation Points

### 1. Session-Based View Tracking
- Track views in session memory, not immediate DB updates
- Update database only on session completion/timeout
- Prevent frequent database writes during active sessions

### 2. User Preference Management
- Progressive restriction: specific campaign → all campaigns
- One-month cooling period for rejected content
- Feedback collection for business insights

### 3. Insight Closure Workflow
```
First Closure  → Hide until next eligible time
Second Closure → "Want to see this again?"
                 ├── Yes → Normal rotation continues
                 └── No  → Collect reason + 1-month wait
                 
Subsequent     → "Want to stop all insights?"
Closures         ├── Yes → Opt out completely + reason
                 └── No  → 1-month wait for this campaign
```

### 4. Database Update Strategy
- **Real-time**: User preferences and closure actions
- **Deferred**: View counts and frequency updates
- **Batch**: Session cleanup and timeout handling

### 5. Integration Points
- **UserCampaignRotationService**: Check preferences before serving
- **Session Management**: Track views without immediate DB impact
- **Preference Service**: Handle closure workflows and feedback
- **Background Jobs**: Handle session timeouts and cleanup

This architecture ensures:
- **Reduced database load** through session-based tracking
- **Better user experience** with progressive restriction options
- **Business intelligence** through feedback collection
- **Scalable session management** using Spring Boot's built-in capabilities
- **Clean separation** between session tracking and persistent data