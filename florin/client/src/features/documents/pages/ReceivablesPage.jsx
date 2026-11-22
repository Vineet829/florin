import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FiBell, FiCheckCircle, FiClock, FiPlus } from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import useListState from "../../../hooks/useListState";
import useLastAnswer from "../../../hooks/useLastAnswer";
import { PAGE_SIZES } from "../../../utils/listState";
import { invoiceCount } from "../../../utils/counts";
import { formatAmount } from "../../../utils/money";
import DocumentsTabs from "../components/DocumentsTabs";
import CurrencyChoice from "../components/CurrencyChoice";
import ReminderDialog from "../components/ReminderDialog";
import {
  useGetAllMyDocsQuery,
  useGetReceivablesQuery,
} from "../documentsApiSlice";
import {
  AGE_BANDS,
  DUE_BANDS,
  MODES,
  bandsOf,
  dueLabel,
  lateLabel,
  readReceivables,
  receivablesQuery,
  receivablesSearch,
} from "../receivablesModel";
import { share } from "../../reports/reportModel";
import { RefusalNote, useBusiness } from "../../business/permissions";
import useBusinessToday from "../../business/useBusinessToday";
import { useGetUserProfileQuery } from "../../users/usersApiSlice";
import {
  WorkspacePage,
  PageHeader,
  ActionLink,
  Panel,
  DataTable,
  Pagination,
  serverPagination,
  EmptyState,
  LoadingState,
  ErrorState,
  Amount,
  DueDate,
  ShareBar,
  money,
} from "../../../components/workspace/Workspace";
import {
  filterChip,
  panel,
  panelNote,
  rowAction,
  skeleton,
  textLink,
} from "../../../components/workspace/pastel";

const NO_INVOICES = [];
const NO_CLIENTS = [];
const DEMO_EMAIL_NOTE = "Emailing is turned off for the shared demo account.";

const VIEWS = {
  "coming-in": {
    group: "current",
    bands: DUE_BANDS,
    bandName: (band) => band.label,
    legend: "Coming in by when it falls due",
    bar: "bg-owed-due",
    figure: "Coming in",
    none: "Nothing is coming in",
    nobody: "No one owes anything that isn't due yet.",
    title: "Invoices coming in",
    order: "Soonest due first. Invoices with no due date come last.",
    empty: {
      title: "Nothing is coming in.",
      text: "Unpaid invoices that aren't due yet show up here, soonest due first.",
      icon: FiClock,
    },
  },
  overdue: {
    group: "overdue",
    bands: AGE_BANDS,
    bandName: (band) => `${band.label} late`,
    legend: "Overdue by days late",
    bar: "bg-owed-overdue",
    figure: "Overdue",
    none: "Nothing is overdue",
    nobody: "No one is late paying.",
    title: "Overdue invoices",
    order: "Most late first. Send a reminder from any of them.",
    empty: {
      title: "Nothing is overdue.",
      text: "Invoices past their due date show up here, most late first, with a way to remind the client.",
      icon: FiCheckCircle,
    },
  },
};

const amountIn = (value, currency, several) =>
  several ? money(value, currency) : formatAmount(value, currency);

const BandBar = ({ bands, byKey }) => {
  const parts = bands.filter((band) => Number(byKey[band.key]?.amount) > 0);
  return (
    <div
      className={`flex h-3.5 gap-0.5 overflow-hidden rounded-full ${parts.length ? "" : "bg-[#2327350d]"}`}
      aria-hidden="true"
    >
      {parts.map((band) => (
        <span
          key={band.key}
          className={`min-w-1 ${band.color}`}
          style={{ flex: `${Number(byKey[band.key].amount)} 1 0` }}
        />
      ))}
    </div>
  );
};

const Legend = ({ view, byKey, currency }) => (
  <div className="[container-type:inline-size]">
    <ul
      className="m-0 grid list-none grid-cols-2 gap-x-6 gap-y-3 p-0 [@container(min-width:560px)]:grid-cols-4"
      aria-label={view.legend}
    >
      {view.bands.map((band) => {
        const { amount = 0, invoices = 0 } = byKey[band.key] || {};
        return (
          <li className="flex min-w-0 flex-col gap-1" key={band.key}>
            <span
              className="flex items-center gap-2 whitespace-nowrap text-[13px] text-spot-muted"
              aria-hidden="true"
            >
              <span
                className={`size-2.5 shrink-0 rounded-[3px] ${band.color}`}
              />
              {band.label}
            </span>
            <span
              className="flex items-baseline gap-1.5 whitespace-nowrap text-[13px] text-spot-muted"
              aria-hidden="true"
            >
              <Amount
                value={amount}
                currency={currency}
                hideCode
                className="text-[17px] font-light tracking-[-0.03em] text-spot-ink"
              />
              · {Number(invoices).toLocaleString("en")}
            </span>
            <span className="sr-only">
              {view.bandName(band)}: {money(amount, currency)} on{" "}
              {invoiceCount(invoices)}
            </span>
          </li>
        );
      })}
    </ul>
  </div>
);

const SummaryPlaceholder = () => (
  <div className={panel} aria-hidden="true">
    <div className="mb-5 flex gap-2 compact:mb-4">
      <span className={`${skeleton} h-9 w-[104px] rounded-full`} />
      <span className={`${skeleton} h-9 w-[88px] rounded-full`} />
    </div>
    <div className="grid min-w-0 gap-x-10 gap-y-6 min-[1100px]:grid-cols-[minmax(0,1fr)_minmax(420px,0.85fr)]">
      <div className="flex min-w-0 flex-col gap-4">
        <span className={`${skeleton} h-[13px] w-[90px] rounded-md`} />
        <span className={`${skeleton} h-9 w-[240px] rounded-md`} />
        <span className={`${skeleton} h-3.5 w-full rounded-full`} />
        <span className={`${skeleton} h-11 w-4/5 rounded-md`} />
      </div>
      <div className="flex min-w-0 flex-col gap-3">
        <span className={`${skeleton} h-3.5 w-[120px] rounded-md`} />
        {Array.from({ length: 4 }, (_, index) => (
          <span className={`${skeleton} h-8 w-full rounded-md`} key={index} />
        ))}
      </div>
    </div>
  </div>
);

const Summary = ({ summary, mode, onMode, onCurrency, busy }) => {
  const view = VIEWS[mode];
  const group = summary[view.group] || {};
  const byKey = bandsOf(group);
  const { currency } = summary;
  const several = summary.currencies?.length > 1;
  const clients = group.clients || NO_CLIENTS;
  const largest = Math.max(0, ...clients.map((row) => Number(row.amount)));
  return (
    <Panel>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-x-4 gap-y-3 compact:mb-4">
        <h2 className="sr-only">What clients owe</h2>
        <fieldset className="m-0 flex min-w-0 flex-wrap gap-2 border-0 p-0">
          <legend className="sr-only">Show</legend>
          {MODES.map((option) => (
            <label key={option.value} className={filterChip}>
              <input
                type="radio"
                name="receivables-mode"
                className="sr-only"
                checked={mode === option.value}
                onChange={() => onMode(option.value)}
              />
              {option.label}
            </label>
          ))}
        </fieldset>
        <CurrencyChoice
          value={currency}
          currencies={summary.currencies}
          onChange={onCurrency}
        />
      </div>
      <div
        className={`grid min-w-0 gap-x-10 gap-y-6 transition-opacity min-[1100px]:grid-cols-[minmax(0,1fr)_minmax(420px,0.85fr)] ${busy ? "opacity-60" : ""}`}
        aria-busy={busy || undefined}
      >
        <div className="flex min-w-0 flex-col gap-4">
          <div>
            <p className="text-[13px] text-spot-body">{view.figure}</p>
            <p className="mt-1 flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <Amount
                value={group.amount}
                currency={currency}
                hideCode={!several}
                className="text-[36px] font-extralight tracking-[-0.04em] text-spot-ink"
              />
              <span className="text-[13px] text-spot-muted">
                {group.invoices
                  ? `on ${invoiceCount(group.invoices)}`
                  : view.none}
              </span>
            </p>
            <p className={`mt-1.5 ${panelNote}`}>
              Of {amountIn(summary.total, currency, several)} owed on{" "}
              {invoiceCount(summary.invoices)}, as of today.
            </p>
          </div>
          <BandBar bands={view.bands} byKey={byKey} />
          <Legend view={view} byKey={byKey} currency={currency} />
        </div>
        <div className="min-w-0">
          <h3 className="mb-1 text-[14px] font-medium text-spot-ink">
            Who owes most
          </h3>
          {clients.length ? (
            <DataTable
              label={`Who owes most, ${view.figure.toLowerCase()}`}
              narrow
              rows={clients}
              rowKey={(row) => row.clientId || `name-${row.client}`}
              columns={[
                {
                  label: "Client",
                  type: "main",
                  render: (row) => (
                    <>
                      {row.clientId ? (
                        <Link
                          className={textLink}
                          to={`/single-customer/${row.clientId}`}
                        >
                          {row.client}
                        </Link>
                      ) : (
                        row.client || "No client"
                      )}
                      <ShareBar
                        ratio={share(row.amount, largest)}
                        tone={view.bar}
                      />
                    </>
                  ),
                },
                {
                  label: "Invoices",
                  type: "number",
                  render: (row) => Number(row.invoices).toLocaleString("en"),
                },
                {
                  label: "Owed",
                  type: "amount",
                  render: (row) => (
                    <Amount
                      value={row.amount}
                      currency={currency}
                      hideCode={!several}
                    />
                  ),
                },
              ]}
            />
          ) : (
            <p className={`mt-2 ${panelNote}`}>{view.nobody}</p>
          )}
        </div>
      </div>
    </Panel>
  );
};

const ReceivablesPage = () => {
  useTitle("Receivables - Florin");
  const [list, update] = useListState(readReceivables, receivablesSearch);
  const view = VIEWS[list.mode];
  const summaryQuery = useGetReceivablesQuery(list.currency || undefined, {
    refetchOnMountOrArgChange: 60,
  });
  const summary = summaryQuery.data;
  const currency = list.currency || summary?.currency;
  const query = useGetAllMyDocsQuery(receivablesQuery(list, currency), {
    skip: !list.currency && !summary,
    refetchOnMountOrArgChange: 60,
  });
  const answered = useLastAnswer(query.currentData, list);
  const shown =
    answered.data && answered.list.mode === list.mode ? answered.data : null;
  const rows = shown?.myDocuments || NO_INVOICES;
  const pagination = serverPagination({
    rows,
    count: shown?.totalDocuments ?? 0,
    page: list.page - 1,
    size: list.size,
    setPage: (page) => update({ page: page + 1 }),
    setSize: (size) => update({ size, page: 1 }),
  });
  const pages = query.currentData?.numberOfPages;
  useEffect(() => {
    if (pages && list.page > pages) update({ page: pages });
  }, [pages, list.page, update]);

  const { reason } = useBusiness();
  const editRefusal = reason("EDIT_RECORDS");
  const demo = useGetUserProfileQuery().data?.userProfile?.demo;
  const sendNote = editRefusal || (demo ? DEMO_EMAIL_NOTE : undefined);
  const [reminding, setReminding] = useState(null);
  const today = useBusinessToday();

  const columns = [
    {
      label: "Invoice",
      type: "main",
      render: (row) => (
        <Link className={textLink} to={`/document/${row._id}`}>
          {row.documentNumber || "No number yet"}
        </Link>
      ),
    },
    { label: "Client", render: (row) => row.customer?.name || "-" },
    {
      label: "Due date",
      render: (row) => <DueDate value={row.dueDate} overdue={row.overdue} />,
    },
    list.mode === "overdue"
      ? {
          label: "Late",
          render: (row) => (
            <span className="text-overdue-strong">
              {lateLabel(row.dueDate, today)}
            </span>
          ),
        }
      : { label: "Due", render: (row) => dueLabel(row.dueDate, today) },
    {
      label: "Balance",
      type: "amount",
      render: (row) => (
        <Amount value={row.balanceDue} currency={row.currency} />
      ),
    },
    ...(list.mode === "overdue"
      ? [
          {
            label: "Remind",
            type: "action",
            render: (row) => (
              <button
                type="button"
                className={rowAction}
                onClick={() => setReminding(row._id)}
                aria-label={`Send a reminder for ${row.documentNumber || "this invoice"}`}
                disabled={Boolean(sendNote)}
                title={sendNote}
                aria-describedby={
                  sendNote ? "receivables-send-note" : undefined
                }
              >
                <FiBell aria-hidden="true" />
                <span>Send reminder</span>
              </button>
            ),
          },
        ]
      : []),
  ];

  return (
    <WorkspacePage section="Documents">
      <PageHeader
        title="Documents"
        description="Invoices, quotations, receipts and credit notes."
        actions={
          <>
            <DocumentsTabs />
            <ActionLink to="/create-doc" reason={editRefusal}>
              <FiPlus aria-hidden="true" /> Create document
            </ActionLink>
          </>
        }
      />
      <RefusalNote id="receivables-send-note" reason={sendNote} />
      {summaryQuery.isError && !summary ? (
        <ErrorState error={summaryQuery.error} retry={summaryQuery.refetch} />
      ) : (
        <>
          {summary ? (
            <Summary
              summary={summary}
              mode={list.mode}
              onMode={(mode) => update({ mode, page: 1 })}
              onCurrency={(chosen) => update({ currency: chosen, page: 1 })}
              busy={summaryQuery.isFetching}
            />
          ) : (
            <SummaryPlaceholder />
          )}
          {!shown && !query.isError ? (
            <LoadingState />
          ) : (
            <Panel title={view.title} description={view.order}>
              {query.isError ? (
                <ErrorState error={query.error} retry={query.refetch} />
              ) : rows.length ? (
                <>
                  <DataTable
                    columns={columns}
                    rows={pagination.rows}
                    label={view.title}
                    busy={query.isFetching}
                  />
                  <Pagination pagination={pagination} sizes={PAGE_SIZES} />
                </>
              ) : (
                <EmptyState
                  title={view.empty.title}
                  text={view.empty.text}
                  icon={view.empty.icon}
                />
              )}
            </Panel>
          )}
        </>
      )}
      <ReminderDialog
        documentId={reminding}
        open={Boolean(reminding)}
        onClose={() => setReminding(null)}
      />
    </WorkspacePage>
  );
};

export default ReceivablesPage;
