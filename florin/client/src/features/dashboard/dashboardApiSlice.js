import { baseApiSlice } from "../api/baseApiSlice";

export const dashboardApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getOverview: builder.query({
      query: ({ currency, period }) =>
        `/report/overview?${new URLSearchParams({
          ...(currency && { currency }),
          period,
        })}`,
      providesTags: [
        "Document",
        "Customer",
        "Expense",
        "Recurring",
        "Settings",
      ],
    }),
  }),
});

export const { useGetOverviewQuery } = dashboardApiSlice;
