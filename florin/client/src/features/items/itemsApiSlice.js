import { baseApiSlice } from "../api/baseApiSlice";

export const itemsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getItems: builder.query({
      query: () => "/item?size=500",
      providesTags: ["Item"],
    }),
    getItemSales: builder.query({
      query: (params = {}) => `/item/sales?${new URLSearchParams(params)}`,
      providesTags: ["Document"],
    }),
    getItemSalesDetail: builder.query({
      query: (params) => `/item/sales/detail?${new URLSearchParams(params)}`,
      providesTags: ["Document"],
    }),
    createItem: builder.mutation({
      query: (item) => ({ url: "/item", method: "POST", body: item }),
      invalidatesTags: ["Item"],
    }),
    updateItem: builder.mutation({
      query: ({ id, ...item }) => ({
        url: `/item/${id}`,
        method: "PATCH",
        body: item,
      }),
      invalidatesTags: ["Item"],
    }),
    deleteItem: builder.mutation({
      query: (id) => ({ url: `/item/${id}`, method: "DELETE" }),
      invalidatesTags: ["Item"],
    }),
  }),
});

export const {
  useGetItemsQuery,
  useGetItemSalesQuery,
  useGetItemSalesDetailQuery,
  useCreateItemMutation,
  useUpdateItemMutation,
  useDeleteItemMutation,
} = itemsApiSlice;
