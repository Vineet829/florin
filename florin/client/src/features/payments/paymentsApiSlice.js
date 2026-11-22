import { baseApiSlice } from "../api/baseApiSlice";

export const paymentsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getPayments: builder.query({
      query: (params = {}) => `/payments?${new URLSearchParams(params)}`,
      providesTags: ["Document"],
    }),
    getPaymentsSummary: builder.query({
      query: (params = {}) => {
        const search = new URLSearchParams(params).toString();
        return `/payments/summary${search ? `?${search}` : ""}`;
      },
      providesTags: ["Document"],
    }),
  }),
});

export const { useGetPaymentsQuery, useGetPaymentsSummaryQuery } =
  paymentsApiSlice;
