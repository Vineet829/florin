import { baseApiSlice } from "../api/baseApiSlice";
import { fetchFile } from "../api/fetchFile";

export const documentsApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getAllMyDocs: builder.query({
      query: (params = {}) => `/document/all?${new URLSearchParams(params)}`,
      providesTags: ["Document"],
    }),
    getDocumentSummary: builder.query({
      query: (currency) =>
        `/document/summary${currency ? `?${new URLSearchParams({ currency })}` : ""}`,
      providesTags: ["Document"],
    }),
    getReceivables: builder.query({
      query: (currency) =>
        `/document/receivables${currency ? `?${new URLSearchParams({ currency })}` : ""}`,
      providesTags: ["Document"],
    }),
    getSales: builder.query({
      query: (params = {}) => {
        const search = new URLSearchParams(params).toString();
        return `/document/sales${search ? `?${search}` : ""}`;
      },
      providesTags: ["Document"],
    }),
    getTaxRates: builder.query({
      query: () => "/document/tax-rates",
      providesTags: ["Document", "Item"],
    }),
    getSingleDoc: builder.query({
      query: (id) => `/document/${id}`,
      providesTags: ["Document"],
    }),
    duplicateDoc: builder.mutation({
      query: (id) => ({ url: `/document/${id}/duplicate`, method: "POST" }),
      invalidatesTags: ["Document"],
    }),
    convertDoc: builder.mutation({
      query: (id) => ({ url: `/document/${id}/convert`, method: "POST" }),
      invalidatesTags: ["Document"],
    }),
    creditDoc: builder.mutation({
      query: (id) => ({ url: `/document/${id}/credit-note`, method: "POST" }),
      invalidatesTags: ["Document"],
    }),
    getShareStatus: builder.query({
      query: (id) => `/document/${id}/share`,
      providesTags: ["Document"],
    }),
    createShareLink: builder.mutation({
      query: (id) => ({ url: `/document/${id}/share`, method: "POST" }),
      invalidatesTags: ["Document"],
    }),
    revokeShareLink: builder.mutation({
      query: (id) => ({ url: `/document/${id}/share`, method: "DELETE" }),
      invalidatesTags: ["Document"],
    }),
    shareOnWhatsApp: builder.mutation({
      query: (id) => ({
        url: `/document/${id}/share/whatsapp`,
        method: "POST",
      }),
      invalidatesTags: ["Document"],
    }),
    getWaitingPaymentClaims: builder.query({
      query: () => "/payment-claims",
      providesTags: ["Document"],
    }),
    getPaymentClaims: builder.query({
      query: (id) => `/document/${id}/payment-claims`,
      providesTags: ["Document"],
    }),
    confirmPaymentClaim: builder.mutation({
      query: ({ id, claimId, ...payment }) => ({
        url: `/document/${id}/payment-claims/${claimId}/confirm`,
        method: "POST",
        body: payment,
      }),
      invalidatesTags: ["Document"],
    }),
    dismissPaymentClaim: builder.mutation({
      query: ({ id, claimId, reason }) => ({
        url: `/document/${id}/payment-claims/${claimId}/dismiss`,
        method: "POST",
        body: { reason },
      }),
      invalidatesTags: ["Document"],
    }),
    getDocumentActivity: builder.query({
      query: (id) => `/document/${id}/activity`,
      providesTags: ["Document"],
    }),
    createDoc: builder.mutation({
      query: (formData) => ({
        url: "/document/create",
        method: "POST",
        body: formData,
      }),
      invalidatesTags: ["Document"],
    }),
    issueDoc: builder.mutation({
      query: ({ id, ...dates }) => ({
        url: `/document/${id}/issue`,
        method: "POST",
        body: dates,
      }),
      invalidatesTags: ["Document"],
    }),
    voidDoc: builder.mutation({
      query: ({ id, reason }) => ({
        url: `/document/${id}/void`,
        method: "POST",
        body: { reason },
      }),
      invalidatesTags: ["Document"],
    }),
    updateDoc: builder.mutation({
      query: ({ id, ...rest }) => ({
        url: `/document/${id}`,
        method: "PATCH",
        body: rest,
      }),
      invalidatesTags: ["Document"],
    }),
    deleteDoc: builder.mutation({
      query: (id) => ({
        url: `/document/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Document"],
    }),
    getEmailDraft: builder.query({
      query: (id) => `/document/${id}/email`,
      keepUnusedDataFor: 0,
    }),
    emailDocument: builder.mutation({
      query: ({ id, ...email }) => ({
        url: `/document/${id}/email`,
        method: "POST",
        body: email,
      }),
      invalidatesTags: ["Document"],
    }),
    getDocumentsCsv: builder.mutation({
      queryFn: (filters, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, `/document/csv?${new URLSearchParams(filters)}`),
    }),
    getPaymentsCsv: builder.mutation({
      queryFn: (_arg, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, "/document/payments/csv"),
    }),
    getDocumentPdf: builder.mutation({
      queryFn: (id, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, `/document/${id}/pdf`),
    }),
    getReminderDraft: builder.query({
      query: (id) => `/document/${id}/remind`,
      keepUnusedDataFor: 0,
    }),
    sendReminder: builder.mutation({
      query: ({ id, ...email }) => ({
        url: `/document/${id}/remind`,
        method: "POST",
        body: email,
      }),
      invalidatesTags: ["Document"],
    }),
    getReceiptPdf: builder.mutation({
      queryFn: ({ id, paymentId }, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, `/document/${id}/payment/${paymentId}/receipt`),
    }),
    getReceiptEmailDraft: builder.query({
      query: ({ id, paymentId }) =>
        `/document/${id}/payment/${paymentId}/receipt/email`,
      keepUnusedDataFor: 0,
    }),
    emailReceipt: builder.mutation({
      query: ({ id, paymentId, ...email }) => ({
        url: `/document/${id}/payment/${paymentId}/receipt/email`,
        method: "POST",
        body: email,
      }),
      invalidatesTags: ["Document"],
    }),
    updatePayment: builder.mutation({
      query: ({ id, paymentId, ...payment }) => ({
        url: `/document/${id}/payment/${paymentId}`,
        method: "PATCH",
        body: payment,
      }),
      invalidatesTags: ["Document"],
    }),
    deletePayment: builder.mutation({
      query: ({ id, paymentId }) => ({
        url: `/document/${id}/payment/${paymentId}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Document"],
    }),
    createPayment: builder.mutation({
      query: ({ id, ...rest }) => ({
        url: `/document/${id}/payment`,
        method: "POST",
        body: rest,
      }),
      invalidatesTags: ["Document"],
    }),
    createRefund: builder.mutation({
      query: ({ id, ...refund }) => ({
        url: `/document/${id}/refund`,
        method: "POST",
        body: refund,
      }),
      invalidatesTags: ["Document"],
    }),
    updateRefund: builder.mutation({
      query: ({ id, refundId, ...refund }) => ({
        url: `/document/${id}/refund/${refundId}`,
        method: "PATCH",
        body: refund,
      }),
      invalidatesTags: ["Document"],
    }),
    deleteRefund: builder.mutation({
      query: ({ id, refundId }) => ({
        url: `/document/${id}/refund/${refundId}`,
        method: "DELETE",
      }),
      invalidatesTags: ["Document"],
    }),
  }),
});

export const {
  useCreatePaymentMutation,
  useDeletePaymentMutation,
  useUpdatePaymentMutation,
  useEmailDocumentMutation,
  useGetEmailDraftQuery,
  useGetDocumentPdfMutation,
  useGetDocumentsCsvMutation,
  useGetPaymentsCsvMutation,
  useGetReceiptPdfMutation,
  useGetReminderDraftQuery,
  useSendReminderMutation,
  useGetReceiptEmailDraftQuery,
  useEmailReceiptMutation,
  useDeleteDocMutation,
  useCreateDocMutation,
  useGetSingleDocQuery,
  useGetTaxRatesQuery,
  useGetDocumentActivityQuery,
  useGetShareStatusQuery,
  useCreateShareLinkMutation,
  useRevokeShareLinkMutation,
  useDuplicateDocMutation,
  useConvertDocMutation,
  useCreditDocMutation,
  useCreateRefundMutation,
  useUpdateRefundMutation,
  useDeleteRefundMutation,
  useGetAllMyDocsQuery,
  useGetDocumentSummaryQuery,
  useGetReceivablesQuery,
  useGetSalesQuery,
  useIssueDocMutation,
  useUpdateDocMutation,
  useVoidDocMutation,
  useShareOnWhatsAppMutation,
  useGetPaymentClaimsQuery,
  useGetWaitingPaymentClaimsQuery,
  useConfirmPaymentClaimMutation,
  useDismissPaymentClaimMutation,
} = documentsApiSlice;
