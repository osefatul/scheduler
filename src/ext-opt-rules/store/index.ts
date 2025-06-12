// store/index.ts
import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';
import { CampaignClosureApi } from '../campaignClosureAPI';
// Import your existing APIs
// import { campaignAPI } from '@/external/services/campaignAPI';
// import { bannerAPI } from '@/external/services/bannerAPI';

export const store = configureStore({
  reducer: {
    // Add the campaign closure API reducer
    [CampaignClosureApi.reducerPath]: CampaignClosureApi.reducer,
    // Add your existing API reducers
    // [campaignAPI.reducerPath]: campaignAPI.reducer,
    // [bannerAPI.reducerPath]: bannerAPI.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActions: ['persist/PERSIST', 'persist/REHYDRATE'],
      },
    })
    .concat(CampaignClosureApi.middleware)
    // .concat(campaignAPI.middleware)
    // .concat(bannerAPI.middleware)
});

// Enable refetchOnFocus/refetchOnReconnect behaviors
setupListeners(store.dispatch);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;