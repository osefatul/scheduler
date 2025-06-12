// campaignClosureAPI.ts
import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react";

export interface InsightClosureRequest {
  userId: string;
  companyId: string;
  campaignId: string;
  closureDate?: string;
}

export interface PreferenceRequest {
  userId: string;
  companyId: string;
  campaignId: string;
  wantsToSee: boolean;
  reason?: string;
  isGlobalResponse: boolean;
  preferenceDate?: string;
}

export interface GlobalOptOutRequest {
  userId: string;
  optOut: boolean;
  reason?: string;
  optOutDate?: string;
}

export interface InsightClosureResponse {
  campaignId: string;
  closureCount: number;
  effectiveDate: string;
  action: string;
  message: string;
  requiresUserInput: boolean;
  isGlobalPrompt?: boolean;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string;
}

export const CampaignClosureApi = createApi({
  reducerPath: "campaignClosureApi",
  baseQuery: fetchBaseQuery({
    baseUrl: window._env?.dcrApiUrl || "/api",
    prepareHeaders: (headers) => {
      headers.set('content-type', 'application/json');
      return headers;
    },
  }),
  tagTypes: ["CampaignClosure", "UserPreference"],
  endpoints: (builder) => ({
    // Close insight/campaign
    closeInsight: builder.mutation<ApiResponse<InsightClosureResponse>, InsightClosureRequest>({
      query: (body) => ({
        url: "/v1/insights/closure/close",
        method: "POST",
        body,
      }),
      invalidatesTags: ["CampaignClosure"],
    }),

    // Set closure preference
    setClosurePreference: builder.mutation<ApiResponse<string>, PreferenceRequest>({
      query: (body) => ({
        url: "/v1/insights/closure/preference", 
        method: "POST",
        body,
      }),
      invalidatesTags: ["CampaignClosure", "UserPreference"],
    }),

    // Global opt-out
    handleGlobalOptOut: builder.mutation<ApiResponse<string>, GlobalOptOutRequest>({
      query: (body) => ({
        url: "/v1/insights/closure/global-optout",
        method: "POST",
        body,
      }),
      invalidatesTags: ["CampaignClosure", "UserPreference"],
    }),

    // Check wait period status
    checkWaitPeriodStatus: builder.query<
      ApiResponse<any>,
      { userId: string; companyId: string; date?: string }
    >({
      query: ({ userId, companyId, date }) =>
        `/v1/insights/closure/check-wait-period/${userId}/${companyId}${
          date ? `?date=${date}` : ""
        }`,
      providesTags: ["CampaignClosure"],
    }),

    // Check opt-out status
    checkOptOutStatus: builder.query<ApiResponse<boolean>, string>({
      query: (userId) => `/v1/insights/closure/check-optout/${userId}`,
      providesTags: ["UserPreference"],
    }),

    // Debug endpoint for development
    debugClosureStatus: builder.query<
      ApiResponse<any>,
      { userId: string; companyId: string; campaignId: string; date?: string }
    >({
      query: ({ userId, companyId, campaignId, date }) =>
        `/v1/insights/closure/debug/closure-status/${userId}/${companyId}/${campaignId}${
          date ? `?date=${date}` : ""
        }`,
      providesTags: ["CampaignClosure"],
    }),
  }),
});

export const {
  useCloseInsightMutation,
  useSetClosurePreferenceMutation,
  useHandleGlobalOptOutMutation,
  useCheckWaitPeriodStatusQuery,
  useCheckOptOutStatusQuery,
  useDebugClosureStatusQuery,
} = CampaignClosureApi;