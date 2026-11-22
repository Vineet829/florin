import { textLink } from "../../../components/workspace/pastel";

const ResultLine = ({
  parts,
  onClear,
  busy = false,
  clearLabel = "Clear filters",
}) => (
  <div
    className={`mb-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 rounded-[14px] bg-spot-brand/[.08] px-3.5 py-2.5 text-[13.5px] text-spot-text transition-opacity ${busy ? "opacity-60" : ""}`}
  >
    <p className="flex min-w-0 flex-wrap items-center gap-x-2.5 gap-y-1">
      {parts.map((part, index) => (
        <span
          key={index}
          className={`whitespace-nowrap max-[700px]:whitespace-normal ${part.tone === "overdue" ? "text-overdue-strong" : ""}`}
        >
          {index > 0 && (
            <>
              <span className="sr-only">, </span>
              <span className="mr-2.5 text-spot-disabled" aria-hidden="true">
                ·
              </span>
            </>
          )}
          {part.before}
          {part.strong && (
            <strong className="font-medium">{part.strong}</strong>
          )}
          {part.after}
        </span>
      ))}
    </p>
    <button
      type="button"
      className={`ml-auto ${textLink} bg-transparent p-0 text-[13px] coarse:min-h-11`}
      onClick={onClear}
    >
      {clearLabel}
    </button>
  </div>
);

export default ResultLine;
