import { createSlice } from "@reduxjs/toolkit";
import { logOut } from "../auth/authSlice";

const initialState = { activeId: null, changedElsewhere: false };

const businessSlice = createSlice({
  name: "business",
  initialState,
  reducers: {
    setActiveBusiness: (state, action) => {
      state.activeId = action.payload;
      state.changedElsewhere = false;
    },
    businessChanged: (state, action) => {
      state.activeId = action.payload;
      state.changedElsewhere = true;
    },
    changeNoticed: (state) => {
      state.changedElsewhere = false;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(logOut, () => initialState);
  },
});

export const { setActiveBusiness, businessChanged, changeNoticed } =
  businessSlice.actions;

export default businessSlice.reducer;
