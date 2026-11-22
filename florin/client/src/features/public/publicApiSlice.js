import { baseApiSlice } from "../api/baseApiSlice";
import { fetchFile } from "../api/fetchFile";

const showAnswer = async (token, { dispatch, queryFulfilled }) => {
  try {
    const { data } = await queryFulfilled;
    dispatch(
      publicApiSlice.util.updateQueryData(
        "getSharedDocument",
        token,
        () => data,
      ),
    );
  } catch {
  }
};

export const publicApiSlice = baseApiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getSharedDocument: builder.query({
      query: (token) => `/public/share/${token}`,
    }),
    acceptSharedDocument: builder.mutation({
      query: (token) => ({
        url: `/public/share/${token}/accept`,
        method: "POST",
      }),
      onQueryStarted: (token, api) => showAnswer(token, api),
    }),
    declineSharedDocument: builder.mutation({
      query: ({ token, reason }) => ({
        url: `/public/share/${token}/decline`,
        method: "POST",
        body: { reason },
      }),
      onQueryStarted: ({ token }, api) => showAnswer(token, api),
    }),
    reportSharedPayment: builder.mutation({
      query: ({ token, ...report }) => ({
        url: `/public/share/${token}/payment-claims`,
        method: "POST",
        body: report,
      }),
      onQueryStarted: ({ token }, api) => showAnswer(token, api),
    }),
    getSharedPdf: builder.mutation({
      queryFn: (token, _api, _extraOptions, baseQuery) =>
        fetchFile(baseQuery, `/public/share/${token}/pdf`),
    }),
  }),
});

export const {
  useGetSharedDocumentQuery,
  useAcceptSharedDocumentMutation,
  useDeclineSharedDocumentMutation,
  useGetSharedPdfMutation,
  useReportSharedPaymentMutation,
} = publicApiSlice;
