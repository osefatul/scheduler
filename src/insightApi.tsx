// Add this to your insightAPI.ts file

// Define a type for standardized insights
export interface StandardizedInsight {
  standardizedText: string;
  companyCount: number;
  companies: string[];
}

// Add this to your existing API definition
export const insightApi = createApi({
  reducerPath: 'insightApi',
  baseQuery: fetchBaseQuery({ baseUrl: '/api' }),
  endpoints: (builder) => ({
    // ... existing endpoints
    
    // New endpoint for standardized insights
    getStandardizedInsights: builder.query<StandardizedInsight[], { insightType: string; insightSubType: string }>({
      query: ({ insightType, insightSubType }) => 
        `/insights/standardized?insightType=${encodeURIComponent(insightType)}&insightSubType=${encodeURIComponent(insightSubType)}`,
    }),
  }),
});

// Export the new hook
export const { 
  // ... existing exports
  useGetStandardizedInsightsQuery,
} = insightApi;