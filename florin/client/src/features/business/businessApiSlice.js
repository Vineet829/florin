import { baseApiSlice } from "../api/baseApiSlice";

export const businessApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getBusinesses: builder.query({
      query: () => "/businesses",
      providesTags: ["Business"],
    }),
    createBusiness: builder.mutation({
      query: (business) => ({
        url: "/businesses",
        method: "POST",
        body: business,
      }),
    }),
    switchBusiness: builder.mutation({
      query: (businessId) => ({
        url: "/businesses/active",
        method: "PUT",
        body: { businessId },
      }),
    }),
    getBusiness: builder.query({
      query: () => "/business",
      providesTags: ["Business"],
    }),
    updateBusiness: builder.mutation({
      query: (business) => ({
        url: "/business",
        method: "PATCH",
        body: business,
      }),
      invalidatesTags: ["Business"],
    }),
    getMembers: builder.query({
      query: () => "/business/members",
      providesTags: ["Team"],
    }),
    changeMemberRole: builder.mutation({
      query: ({ userId, role }) => ({
        url: `/business/members/${userId}`,
        method: "PATCH",
        body: { role },
      }),
      invalidatesTags: ["Team"],
    }),
    removeMember: builder.mutation({
      query: (userId) => ({
        url: `/business/members/${userId}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Team"],
    }),
    transferBusiness: builder.mutation({
      query: (userId) => ({
        url: "/business/transfer",
        method: "POST",
        body: { userId },
      }),
      invalidatesTags: ["Team", "Business"],
    }),
    leaveBusiness: builder.mutation({
      query: () => ({ url: "/business/leave", method: "POST" }),
    }),
    deleteBusiness: builder.mutation({
      query: (confirmation) => ({
        url: "/business",
        method: "DELETE",
        body: confirmation,
      }),
    }),
    getInvitations: builder.query({
      query: () => "/business/invitations",
      providesTags: ["Invitation"],
    }),
    inviteMember: builder.mutation({
      query: (invitation) => ({
        url: "/business/invitations",
        method: "POST",
        body: invitation,
      }),
      invalidatesTags: ["Invitation"],
    }),
    resendInvitation: builder.mutation({
      query: (id) => ({
        url: `/business/invitations/${id}/resend`,
        method: "POST",
      }),
      invalidatesTags: ["Invitation"],
    }),
    revokeInvitation: builder.mutation({
      query: (id) => ({ url: `/business/invitations/${id}`, method: "DELETE" }),
      invalidatesTags: ["Invitation"],
    }),
    getTeamActivity: builder.query({
      query: ({ page, size }) => `/business/activity?page=${page}&size=${size}`,
      providesTags: ["Team", "Invitation"],
    }),
    getInvitation: builder.query({
      query: (token) => `/public/invitations/${token}`,
    }),
    acceptInvitation: builder.mutation({
      query: (token) => ({
        url: `/invitations/${token}/accept`,
        method: "POST",
      }),
    }),
  }),
});

export const {
  useAcceptInvitationMutation,
  useChangeMemberRoleMutation,
  useCreateBusinessMutation,
  useDeleteBusinessMutation,
  useGetBusinessQuery,
  useGetBusinessesQuery,
  useGetInvitationQuery,
  useGetInvitationsQuery,
  useGetMembersQuery,
  useGetTeamActivityQuery,
  useInviteMemberMutation,
  useLeaveBusinessMutation,
  useRemoveMemberMutation,
  useResendInvitationMutation,
  useRevokeInvitationMutation,
  useSwitchBusinessMutation,
  useTransferBusinessMutation,
  useUpdateBusinessMutation,
} = businessApiSlice;
