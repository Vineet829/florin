import { baseApiSlice } from "../api/baseApiSlice";

export const notificationsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getNotifications: builder.query({
      query: () => "/notification",
      providesTags: ["Notification"],
    }),
    markNotificationsRead: builder.mutation({
      query: (id) => ({
        url: "/notification/read",
        method: "POST",
        body: id ? { id } : {},
      }),
      invalidatesTags: ["Notification"],
    }),
  }),
});

export const { useGetNotificationsQuery, useMarkNotificationsReadMutation } =
  notificationsApiSlice;
