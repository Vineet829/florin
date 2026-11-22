import { baseApiSlice } from "../api/baseApiSlice";

export const gstApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getGstStates: builder.query({
      query: () => "/gst/states",
      keepUnusedDataFor: 3600,
    }),
  }),
});

export const { useGetGstStatesQuery } = gstApiSlice;
