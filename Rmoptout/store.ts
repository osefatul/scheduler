import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';
import { rmcampaignUsersAPI } from '@/internal/services/rmcampaignUsersAPI';
import userSelectionReducer from './userSelectionSlice';

export const store = configureStore({
  reducer: {
    // Add the RTK Query API reducer
    [rmcampaignUsersAPI.reducerPath]: rmcampaignUsersAPI.reducer,
    userSelection: userSelectionReducer,
  },
  // Add the API middleware
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(rmcampaignUsersAPI.middleware),
});

// Setup listeners for RTK Query
setupListeners(store.dispatch);

// Export types
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;