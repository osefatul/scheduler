import { createSlice, PayloadAction } from '@reduxjs/toolkit';

// Define types for our state
interface UserData {
  userName: string;
  emailId: string;
  companyName: string;
  name: string;
  usbExternalId: string;
  telephoneNumber: string;
  unEnrollReason?: string;
}

interface UserSelectionState {
  selectedUsers: UserData[];
  currentCampaignId: string;
}

// Initial state
const initialState: UserSelectionState = {
  selectedUsers: [],
  currentCampaignId: '',
};

// Create the slice
const userSelectionSlice = createSlice({
  name: 'userSelection',
  initialState,
  reducers: {
    // Set the current campaign ID
    setCampaignId: (state, action: PayloadAction<string>) => {
      state.currentCampaignId = action.payload;
    },
    
    // Add a user to the selection
    addSelectedUser: (state, action: PayloadAction<UserData>) => {
      const newUser = action.payload;
      // Check if the user is already selected
      const userExists = state.selectedUsers.some(
        user => user.userName === newUser.userName
      );
      
      if (!userExists) {
        state.selectedUsers.push(newUser);
      }
    },
    
    // Add multiple users to the selection
    addSelectedUsers: (state, action: PayloadAction<UserData[]>) => {
      const newUsers = action.payload;
      
      newUsers.forEach(newUser => {
        // Check if the user is already selected
        const userExists = state.selectedUsers.some(
          user => user.userName === newUser.userName
        );
        
        if (!userExists) {
          state.selectedUsers.push(newUser);
        }
      });
    },
    
    // Remove a user from selection
    removeSelectedUser: (state, action: PayloadAction<string>) => {
      const userName = action.payload;
      state.selectedUsers = state.selectedUsers.filter(
        user => user.userName !== userName
      );
    },
    
    // Update selection based on current page
    updateSelectionFromPage: (
      state, 
      action: PayloadAction<{
        currentPageData: UserData[];
        rowSelection: Record<string, boolean>;
      }>
    ) => {
      const { currentPageData, rowSelection } = action.payload;
      
      // Get usernames of all users on the current page
      const currentPageUsernames = currentPageData.map(user => user.userName);
      
      // Filter out users that are on the current page (we'll add them back if they're selected)
      state.selectedUsers = state.selectedUsers.filter(
        user => !currentPageUsernames.includes(user.userName)
      );
      
      // Add selected users from the current page
      Object.keys(rowSelection)
        .filter(key => rowSelection[key]) // Only include selected rows
        .forEach(rowId => {
          const index = parseInt(rowId);
          if (index >= 0 && index < currentPageData.length) {
            const user = currentPageData[index];
            state.selectedUsers.push(user);
          }
        });
    },
    
    // Clear all selected users
    clearSelectedUsers: (state) => {
      state.selectedUsers = [];
    }
  }
});

// Export actions and reducer
export const {
  setCampaignId,
  addSelectedUser,
  addSelectedUsers,
  removeSelectedUser,
  updateSelectionFromPage,
  clearSelectedUsers
} = userSelectionSlice.actions;

export default userSelectionSlice.reducer;