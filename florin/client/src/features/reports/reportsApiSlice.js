import { baseApiSlice } from "../api/baseApiSlice";
import { fetchFile } from "../api/fetchFile";

export const reportsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getReport: builder.query({
      query: ({ period, view }) =>
        `/report?${new URLSearchParams({ period, ...(view && { view }) })}`,
      providesTags: ["Document", "Customer"],
    }),
    getTaxReport: builder.query({
      query: ({ period, view }) =>
        `/report/tax?${new URLSearchParams({ period, ...(view && { view }) })}`,
      providesTags: ["Document", "Customer"],
    }),
    getProfitReport: builder.query({
      query: (period) => `/report/profit?${new URLSearchParams({ period })}`,
      providesTags: ["Document", "Expense"],
    }),
    getTdsReport: builder.query({
      query: (period) => `/report/tds?${new URLSearchParams({ period })}`,
      providesTags: ["Document", "Customer"],
    }),
    getGstReturns: builder.query({
      query: ({ from, to }) =>
        `/report/gst?${new URLSearchParams({ from, to })}`,
      providesTags: ["Document", "Customer"],
    }),
    getGstCsv: builder.mutation({
      queryFn: ({ section, from, to }, _api, _extraOptions, baseQuery) =>
        fetchFile(
          baseQuery,
          `/report/gst/${section}/csv?${new URLSearchParams({ from, to })}`,
        ),
    }),
    getReportCsv: builder.mutation({
      queryFn: ({ section, period, view }, _api, _extraOptions, baseQuery) =>
        fetchFile(
          baseQuery,
          `/report/csv/${section}?${new URLSearchParams({ period, ...(view && { view }) })}`,
        ),
    }),
  }),
});

export const {
  useGetReportQuery,
  useGetTaxReportQuery,
  useGetTdsReportQuery,
  useGetProfitReportQuery,
  useGetReportCsvMutation,
  useGetGstReturnsQuery,
  useGetGstCsvMutation,
} = reportsApiSlice;
