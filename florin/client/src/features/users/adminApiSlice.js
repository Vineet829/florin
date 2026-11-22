import { baseApiSlice } from "../api/baseApiSlice";

const USERS = [{ type: "User", id: "LIST" }];

export const adminApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getUsersPage: builder.query({
      query: ({ page, size, q }) =>
        `/user/all?${new URLSearchParams({
          pageNumber: page,
          size,
          ...(q && { q }),
        })}`,
      providesTags: USERS,
    }),
    getAdminStats: builder.query({
      query: () => "/user/stats",
      providesTags: USERS,
    }),
    reactivateUser: builder.mutation({
      query: (id) => ({ url: `/user/${id}/reactivate`, method: "PATCH" }),
      invalidatesTags: [...USERS, "Activity"],
    }),
    setUserAdmin: builder.mutation({
      query: ({ id, admin }) => ({
        url: `/user/${id}/admin`,
        method: "PATCH",
        body: { admin },
      }),
      invalidatesTags: [...USERS, "Activity"],
    }),
  }),
});

export const {
  useGetAdminStatsQuery,
  useGetUsersPageQuery,
  useReactivateUserMutation,
  useSetUserAdminMutation,
} = adminApiSlice;
