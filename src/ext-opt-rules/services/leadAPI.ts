// LeadAPI.ts - Updated to use unified lead endpoints

import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

// Define the API base
export const leadAPI = createApi({
  reducerPath: 'leadAPI',
  baseQuery: fetchBaseQuery({
    baseUrl: '/api/v1/lead/',
    prepareHeaders: (headers) => {
      headers.set('Content-Type', 'application/json');
      return headers;
    },
  }),
  tagTypes: ['Lead', 'WarmLead'],
  endpoints: (builder) => ({
    
    // =============== HOT LEAD ENDPOINTS ===============
    
    /**
     * Create a hot lead (when user clicks "Talk to RM")
     */
    createLead: builder.mutation({
      query: (leadData) => ({
        url: 'hot/create',
        method: 'POST',
        body: leadData,
      }),
      invalidatesTags: ['Lead'],
    }),

    /**
     * Delete a hot lead
     */
    deleteLead: builder.mutation({
      query: (leadData) => ({
        url: 'hot/delete',
        method: 'DELETE',
        body: leadData,
      }),
      invalidatesTags: ['Lead'],
    }),

    // =============== WARM LEAD ENDPOINTS ===============
    
    /**
     * Track warm lead visit (page visit tracking)
     */
    trackWarmLead: builder.mutation({
      query: (warmLeadData) => ({
        url: 'warm/track',
        method: 'POST',
        body: warmLeadData,
      }),
      invalidatesTags: ['WarmLead'],
    }),

    /**
     * Get warm lead statistics
     */
    getWarmLeadStats: builder.query({
      query: ({ userIdentifier, campaignId, insightSubType }) => 
        `warm/stats?userIdentifier=${userIdentifier}&campaignId=${campaignId}&insightSubType=${insightSubType}`,
      providesTags: ['WarmLead'],
    }),

    /**
     * Convert warm lead to hot lead manually
     */
    convertWarmToHot: builder.mutation({
      query: ({ userIdentifier, campaignId, insightSubType }) => ({
        url: `warm/convert-to-hot?userIdentifier=${userIdentifier}&campaignId=${campaignId}&insightSubType=${insightSubType}`,
        method: 'POST',
      }),
      invalidatesTags: ['Lead', 'WarmLead'],
    }),

    // =============== COMBINED OPERATIONS ===============
    
    /**
     * Complete conversion flow - track warm lead and convert to hot in one call
     */
    completeConversionFlow: builder.mutation({
      query: (leadData) => ({
        url: 'convert-complete',
        method: 'POST',
        body: leadData,
      }),
      invalidatesTags: ['Lead', 'WarmLead'],
    }),

    // =============== ANALYTICS ENDPOINTS ===============
    
    /**
     * Get campaign lead analytics
     */
    getCampaignAnalytics: builder.query({
      query: (campaignId) => `analytics/campaign/${campaignId}`,
      providesTags: ['Lead', 'WarmLead'],
    }),

    /**
     * Health check for lead services
     */
    getLeadServiceHealth: builder.query({
      query: () => 'health',
    }),
  }),
});

// Export hooks for usage in components
export const {
  // Hot lead hooks
  useCreateLeadMutation,
  useDeleteLeadMutation,
  
  // Warm lead hooks
  useTrackWarmLeadMutation,
  useGetWarmLeadStatsQuery,
  useConvertWarmToHotMutation,
  
  // Combined operation hooks
  useCompleteConversionFlowMutation,
  
  // Analytics hooks
  useGetCampaignAnalyticsQuery,
  useGetLeadServiceHealthQuery,
} = leadAPI;

// Type definitions for better TypeScript support
export interface WarmLeadData {
  userIdentifier: string;
  campaignId: string;
  insightSubType: string;
  userAgent?: string;
  referrerUrl?: string;
}

export interface HotLeadData {
  leadId: string;
  campaignId: string;
  firstName: string;
  lastName: string;
  emailAddress: string;
  companyName: string;
  phoneNumber: string;
  comments?: string;
  marketingRelationshipType: string;
  corporateConnectionInsight: string;
  userIdentifier?: string;
}

export interface LeadResponse {
  id?: string;
  message: string;
  // Add other response fields as needed
}

// Utility function for tracking warm leads with error handling
export const trackWarmLeadSafely = async (
  trackWarmLead: any,
  warmLeadData: WarmLeadData
): Promise<boolean> => {
  try {
    await trackWarmLead(warmLeadData).unwrap();
    console.log('Warm lead tracked successfully:', warmLeadData);
    return true;
  } catch (error) {
    console.error('Error tracking warm lead:', error);
    return false;
  }
};

// Utility function for creating hot leads with error handling
export const createHotLeadSafely = async (
  createLead: any,
  hotLeadData: HotLeadData
): Promise<LeadResponse | null> => {
  try {
    const result = await createLead(hotLeadData).unwrap();
    console.log('Hot lead created successfully:', result);
    return result;
  } catch (error) {
    console.error('Error creating hot lead:', error);
    return null;
  }
};