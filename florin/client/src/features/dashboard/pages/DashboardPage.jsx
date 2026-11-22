import { Link, useSearchParams } from "react-router-dom";
import { FiAlertCircle } from "react-icons/fi";
import useTitle from "../../../hooks/useTitle";
import { useGetOverviewQuery } from "../dashboardApiSlice";
import {
  DEFAULT_PERIOD,
  PERIODS,
  findPeriod,
  periodSubtitle,
} from "../dashboardModel";
import DashboardShell from "../components/DashboardShell";
import {
  DashboardLoading,
  listRow,
  owedRow,
} from "../components/DashboardPanels";
import ReceivablesCard from "../components/ReceivablesCard";
import OverviewTiles from "../components/OverviewTiles";
import CashFlowPanel from "../components/CashFlowPanel";
import AttentionPanel from "../components/AttentionPanel";
import TopClientsPanel from "../components/TopClientsPanel";
import TopItemsPanel from "../components/TopItemsPanel";
import ComingUpPanel from "../components/ComingUpPanel";
import Shortcuts from "../components/Shortcuts";
import {
  errorBanner,
  errorDetail,
  errorTitle,
  pageSubtitle,
  pageTitle,
  retryButton,
} from "../../../components/workspace/pastel";
import { Select } from "../../../components/workspace/Select";

const OVERVIEW_FIELDS = ["period", "totals", "owedNow", "trend"];

const periodPill =
  "inline-flex min-h-8 items-center justify-center whitespace-nowrap rounded-[11px] px-3.5 text-[13.5px] transition-colors coarse:min-h-11 max-[700px]:flex-1 max-[700px]:px-2";

const DashboardPage = () => {
  useTitle("Business overview - Florin");
  const [params, setParams] = useSearchParams();
  const period = findPeriod(params.get("period")).key;
  const overviewQuery = useGetOverviewQuery(
    { currency: params.get("currency") || undefined, period },
    { refetchOnMountOrArgChange: 60 },
  );
  const answer = overviewQuery.data;
  const snapshot =
    answer && OVERVIEW_FIELDS.every((key) => answer[key]) ? answer : undefined;
  const refreshing = overviewQuery.isFetching;
  const hasError = overviewQuery.isError || (Boolean(answer) && !snapshot);
  const stale = Boolean(snapshot) && !overviewQuery.currentData;
  const shownPeriod = snapshot && findPeriod(snapshot.period.key);
  const failure = !snapshot
    ? [
        "We couldn't load your overview.",
        "Please try again to see your latest figures.",
      ]
    : stale && shownPeriod.key !== period
      ? [
          `We couldn't load ${findPeriod(period).called}.`,
          `Showing ${shownPeriod.called}.`,
        ]
      : stale
        ? [
            `We couldn't load the figures in ${params.get("currency") || snapshot.currencies[0]}.`,
            `Showing ${snapshot.currency}.`,
          ]
        : [
            "Some workspace data couldn't be refreshed.",
            "Your last loaded figures are still shown. Try refreshing.",
          ];
  const search = (changes) => {
    const chosen = { period, currency: params.get("currency"), ...changes };
    const next = new URLSearchParams();
    if (chosen.period !== DEFAULT_PERIOD) next.set("period", chosen.period);
    if (chosen.currency && chosen.currency !== snapshot?.currencies[0]) {
      next.set("currency", chosen.currency);
    }
    return next.toString() ? `?${next}` : "";
  };
  const chooseCurrency = (currency) =>
    setParams(search({ currency }), { replace: true });

  const refresh = () => overviewQuery.refetch();
  const lists = [
    snapshot?.topClients && (
      <TopClientsPanel
        key="top-clients"
        snapshot={snapshot}
        currencyChosen={Boolean(params.get("currency"))}
      />
    ),
    snapshot?.topItems && (
      <TopItemsPanel
        key="top-items"
        snapshot={snapshot}
        currencyChosen={Boolean(params.get("currency"))}
      />
    ),
    snapshot?.comingUp && (
      <ComingUpPanel
        key="coming-up"
        snapshot={snapshot}
        currencyChosen={Boolean(params.get("currency"))}
      />
    ),
  ].filter(Boolean);

  return (
    <DashboardShell>
      <div className="flex min-w-0 flex-col gap-6 compact:gap-5 max-[700px]:gap-5">
        <header className="flex animate-ov-rise flex-wrap items-end justify-between gap-x-8 gap-y-4">
          <div className="min-w-0">
            <h1 className={pageTitle}>
              Business overview<span>.</span>
            </h1>
            <p className={pageSubtitle}>
              {snapshot
                ? periodSubtitle(snapshot.period, snapshot.totals.hasPrevious)
                : findPeriod(period).name}
            </p>
            <Shortcuts />
          </div>
          <div className="flex flex-wrap items-center gap-3 max-[700px]:w-full">
            <nav
              aria-label="Period"
              className="inline-flex gap-1 rounded-[14px] border border-white/90 bg-white/55 p-[3px] max-[700px]:w-full"
            >
              {PERIODS.map(({ key, label }) => (
                <Link
                  key={key}
                  to={{ search: search({ period: key }) }}
                  replace
                  aria-current={key === period ? "true" : undefined}
                  className={`${periodPill} ${key === period ? "bg-white text-spot-ink shadow-key" : "text-spot-nav hover:bg-white/60 hover:text-spot-night"}`}
                >
                  {label}
                </Link>
              ))}
            </nav>
            {snapshot?.currencies.length > 1 && (
              <label className="flex items-center gap-2 text-[13px] text-spot-body">
                Currency
                <Select
                  size="toolbar"
                  value={snapshot.currency}
                  onChange={chooseCurrency}
                >
                  {snapshot.currencies.map((currency) => (
                    <option key={currency} value={currency}>
                      {currency}
                    </option>
                  ))}
                </Select>
              </label>
            )}
          </div>
        </header>
        {hasError && (
          <div className={errorBanner} role="alert">
            <FiAlertCircle aria-hidden="true" />
            <div className="flex-1">
              <strong className={errorTitle}>{failure[0]}</strong>
              <span className={errorDetail}>{failure[1]}</span>
            </div>
            <button
              type="button"
              className={retryButton}
              onClick={refresh}
              disabled={refreshing}
            >
              Try again
            </button>
          </div>
        )}
        {overviewQuery.isLoading ? (
          <DashboardLoading />
        ) : (
          snapshot && (
            <div
              className={`flex min-w-0 flex-col gap-6 compact:gap-5 max-[700px]:gap-5 transition-opacity ${stale ? "opacity-60" : ""}`}
              aria-busy={stale || undefined}
            >
              <OverviewTiles
                snapshot={snapshot}
                currencyChosen={Boolean(params.get("currency"))}
              />
              <div className={owedRow}>
                <ReceivablesCard
                  snapshot={snapshot}
                  currencyChosen={Boolean(params.get("currency"))}
                />
                <AttentionPanel snapshot={snapshot} />
              </div>
              <CashFlowPanel snapshot={snapshot} />
              {lists.length > 0 && (
                <div className={listRow(lists.length)}>{lists}</div>
              )}
            </div>
          )
        )}
      </div>
    </DashboardShell>
  );
};

export default DashboardPage;
