import { baseApiSlice } from "../api/baseApiSlice";

export const recurringApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getSchedules: builder.query({
      query: () => "/recurring",
      providesTags: ["Recurring"],
    }),
    getDocumentSchedule: builder.query({
      query: (documentId) => `/document/${documentId}/recurring`,
      providesTags: ["Recurring"],
    }),
    createSchedule: builder.mutation({
      query: (schedule) => ({
        url: "/recurring",
        method: "POST",
        body: schedule,
      }),
      invalidatesTags: ["Recurring"],
    }),
    updateSchedule: builder.mutation({
      query: ({ id, ...schedule }) => ({
        url: `/recurring/${id}`,
        method: "PATCH",
        body: schedule,
      }),
      invalidatesTags: ["Recurring"],
    }),
    setSchedulePaused: builder.mutation({
      query: ({ id, paused }) => ({
        url: `/recurring/${id}/${paused ? "pause" : "resume"}`,
        method: "POST",
      }),
      invalidatesTags: ["Recurring"],
    }),
    deleteSchedule: builder.mutation({
      query: (id) => ({ url: `/recurring/${id}`, method: "DELETE" }),
      invalidatesTags: ["Recurring"],
    }),
  }),
});

export const {
  useGetSchedulesQuery,
  useGetDocumentScheduleQuery,
  useCreateScheduleMutation,
  useUpdateScheduleMutation,
  useSetSchedulePausedMutation,
  useDeleteScheduleMutation,
} = recurringApiSlice;
