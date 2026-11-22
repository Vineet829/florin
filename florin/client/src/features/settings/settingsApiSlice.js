import { baseApiSlice } from "../api/baseApiSlice";
import { fetchFile } from "../api/fetchFile";

export const settingsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getSettings: builder.query({
      query: () => "/settings",
      providesTags: ["Settings"],
    }),
    updateSettings: builder.mutation({
      query: (settings) => ({
        url: "/settings",
        method: "PUT",
        body: settings,
      }),
      invalidatesTags: ["Settings"],
    }),
    getPaymentDetails: builder.query({
      query: () => "/settings/payment-details",
      providesTags: ["Settings"],
    }),
    updatePaymentDetails: builder.mutation({
      query: (details) => ({
        url: "/settings/payment-details",
        method: "PUT",
        body: details,
      }),
      invalidatesTags: ["Settings"],
    }),
    getEmailTemplates: builder.query({
      query: () => "/settings/email-templates",
      providesTags: ["Settings"],
    }),
    saveEmailTemplate: builder.mutation({
      query: ({ kind, ...template }) => ({
        url: `/settings/email-templates/${kind}`,
        method: "PUT",
        body: template,
      }),
      invalidatesTags: ["Settings"],
    }),
    resetEmailTemplate: builder.mutation({
      query: (kind) => ({
        url: `/settings/email-templates/${kind}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Settings"],
    }),
    previewEmailTemplate: builder.mutation({
      query: ({ kind, ...template }) => ({
        url: `/settings/email-templates/${kind}/preview`,
        method: "POST",
        body: template,
      }),
    }),
    getDataExport: builder.mutation({
      queryFn: (_arg, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, "/export/all"),
    }),
  }),
});

export const {
  useGetDataExportMutation,
  useGetEmailTemplatesQuery,
  useGetPaymentDetailsQuery,
  useGetSettingsQuery,
  usePreviewEmailTemplateMutation,
  useResetEmailTemplateMutation,
  useSaveEmailTemplateMutation,
  useUpdatePaymentDetailsMutation,
  useUpdateSettingsMutation,
} = settingsApiSlice;
