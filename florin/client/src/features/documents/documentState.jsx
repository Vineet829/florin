import { Badge, StatusBadge } from "../../components/workspace/Workspace";

export const isDraft = (document) => document?.state === "draft";

export const isVoid = (document) => document?.state === "void";

export const documentName = (document) =>
  document?.documentNumber ||
  (isDraft(document)
    ? `Draft ${document.documentType.toLowerCase()}`
    : "Document");

export const isCredited = (document) =>
  document?.status === "Paid" &&
  Number(document.totalAmountReceived) === 0 &&
  Number(document.ledger?.totalCredited) > 0;

export const DocumentStatusBadge = ({ document }) =>
  isDraft(document) ? (
    <Badge>Draft</Badge>
  ) : isVoid(document) ? (
    <Badge tone="unpaid">Void</Badge>
  ) : document.documentType === "Credit Note" ? (
    <Badge tone="paid">Issued</Badge>
  ) : isCredited(document) ? (
    <Badge tone="paid">Credited</Badge>
  ) : document.documentType === "Quotation" ? (
    document.acceptedAt ? (
      <Badge tone="paid">Accepted</Badge>
    ) : document.declinedAt ? (
      <Badge tone="unpaid">Declined</Badge>
    ) : document.expired ? (
      <Badge tone="partial">Expired</Badge>
    ) : (
      <Badge>Open</Badge>
    )
  ) : (
    <StatusBadge status={document.status} />
  );
