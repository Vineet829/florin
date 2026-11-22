import {
  Children,
  Fragment,
  isValidElement,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";
import { FiCheck, FiChevronDown, FiSearch } from "react-icons/fi";
import {
  selectFooter,
  selectGroup,
  selectMenu,
  selectOption,
  selectOptionActive,
  selectOptionChosen,
  selectOptionChosenActive,
  selectSearch,
  selectSheet,
  selectSheetSearch,
  selectTrigger,
} from "./pastel";

const GAP = 4;
const MAX_WIDTH = 420;
const EDGE = 8;
const MAX_HEIGHT = 320;
const TYPE_AHEAD_MS = 500;
const SEARCH_ABOVE = 12;

const media = (query) => {
  try {
    return window.matchMedia(query).matches;
  } catch {
    return false;
  }
};
const usesSheet = () => media("(pointer: coarse)") || window.innerWidth < 700;

const textOf = (children) =>
  Children.toArray(children)
    .filter((child) => typeof child === "string" || typeof child === "number")
    .join("");

const parseOptions = (children) => {
  const options = [];
  const add = (element, group) => {
    if (!isValidElement(element) || element.type !== "option") return;
    const label = textOf(element.props.children);
    options.push({
      value: String(element.props.value ?? label),
      label,
      disabled: Boolean(element.props.disabled),
      group,
    });
  };
  Children.forEach(children, (child) => {
    if (!isValidElement(child)) return;
    if (child.type === "optgroup") {
      Children.forEach(child.props.children, (option) =>
        add(option, child.props.label),
      );
    } else {
      add(child);
    }
  });
  return options;
};
const normalize = (options) =>
  options.map((option, index) => ({
    ...option,
    index,
    value: String(option.value),
    label: option.label ?? String(option.value),
  }));

const matches = (option, term) =>
  [option.label, option.description, option.meta].some(
    (text) => text && String(text).toLowerCase().includes(term),
  );
const firstEnabled = (options, from = 0, step = 1) => {
  for (let i = from; i >= 0 && i < options.length; i += step) {
    if (!options[i].disabled) return i;
  }
  return -1;
};

const labelText = (trigger, wrapper) => {
  const label = trigger?.labels?.[0];
  if (!label) return "";
  return [...label.childNodes]
    .filter((node) => !node.contains(wrapper))
    .map((node) => node.textContent)
    .join("")
    .replace(/\s*\*\s*$/, "")
    .trim();
};

export const Select = ({
  value,
  onChange,
  options: given,
  children,
  placeholder = "Choose...",
  size = "form",
  searchable: searchableGiven,
  label,
  id: givenId,
  name,
  disabled = false,
  invalid = false,
  required = false,
  className = "",
  autoFocus = false,
  fit = "longest",
  "aria-label": ariaLabel,
  "aria-labelledby": labelledBy,
  "aria-describedby": describedBy,
  ...rest
}) => {
  const generatedId = useId();
  const id = givenId || `${generatedId}select`;
  const listId = `${id}-list`;
  const optionId = (option) => `${id}-option-${option.index}`;
  const wrapper = useRef(null);
  const trigger = useRef(null);
  const menu = useRef(null);
  const sheetRef = useRef(null);
  const search = useRef(null);
  const typed = useRef({ text: "", at: 0 });
  const natural = useRef(null);
  const placedAt = useRef(null);
  const [open, setOpen] = useState(false);
  const [sheet, setSheet] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(-1);
  const [placement, setPlacement] = useState(null);
  const [missing, setMissing] = useState(false);
  const [title, setTitle] = useState("");

  const options = normalize(given || parseOptions(children));
  const current = String(value ?? "");
  const chosen = options.find((option) => option.value === current);
  const searchable = searchableGiven ?? options.length > SEARCH_ABOVE;
  const term = query.trim().toLowerCase();
  const shown =
    searchable && term
      ? options.filter((option) => matches(option, term))
      : options;
  const activeOption = open ? shown[active] : undefined;

  const openMenu = ({ at, step = 0, typedChar = "" } = {}) => {
    if (disabled || open) return;
    let start;
    if (typedChar) {
      start = firstEnabled(
        options.filter((option) => matches(option, typedChar.toLowerCase())),
      );
    } else {
      const selected = options.findIndex((option) => option.value === current);
      start = at ?? (selected >= 0 ? selected : firstEnabled(options));
      if (step && start >= 0) {
        const next = firstEnabled(options, start + step, step);
        if (next >= 0) start = next;
      }
    }
    setSheet(usesSheet());
    setQuery(typedChar);
    setPlacement(null);
    natural.current = null;
    placedAt.current = null;
    setActive(start);
    setTitle(label || ariaLabel || labelText(trigger.current, wrapper.current));
    setOpen(true);
    if (!searchable) trigger.current?.focus();
  };
  const close = (refocus = true) => {
    setOpen(false);
    setPlacement(null);
    typed.current = { text: "", at: 0 };
    if (refocus) trigger.current?.focus();
  };
  const choose = (option) => {
    if (!option || option.disabled) return;
    close();
    if (option.value !== current) onChange?.(option.value);
  };

  const move = (step) => {
    if (!shown.length) return;
    let next = Math.min(Math.max(active + step, 0), shown.length - 1);
    const direction = step < 0 ? -1 : 1;
    while (next >= 0 && next < shown.length && shown[next].disabled)
      next += direction;
    if (next >= 0 && next < shown.length) setActive(next);
  };
  const jump = (toEnd) => {
    const next = toEnd
      ? firstEnabled(shown, shown.length - 1, -1)
      : firstEnabled(shown);
    if (next >= 0) setActive(next);
  };
  const typeAhead = (char, from = active) => {
    const now = Date.now();
    const word =
      now - typed.current.at < TYPE_AHEAD_MS ? typed.current.text + char : char;
    typed.current = { text: word, at: now };
    const text = word.toLowerCase();
    const repeated = text.length > 1 && [...text].every((c) => c === text[0]);
    const needle = repeated ? text[0] : text;
    const start = text.length === 1 || repeated ? from + 1 : from;
    for (let n = 0; n < shown.length; n += 1) {
      const index = (start + n + shown.length) % shown.length;
      const option = shown[index];
      if (!option.disabled && option.label.toLowerCase().startsWith(needle))
        return index;
    }
    return -1;
  };
  const printable = (event) =>
    event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey;

  const onKeyDown = (event) => {
    const { key } = event;
    if (!open) {
      const opener = {
        Enter: {},
        " ": {},
        ArrowDown: { step: 1 },
        ArrowUp: { step: -1 },
        Home: { at: firstEnabled(options) },
        End: { at: firstEnabled(options, options.length - 1, -1) },
      }[key];
      if (opener) {
        event.preventDefault();
        openMenu(opener);
      } else if (printable(event)) {
        event.preventDefault();
        if (searchable) {
          openMenu({ typedChar: key });
        } else {
          const selected = options.findIndex((o) => o.value === current);
          const found = typeAhead(key, selected);
          openMenu({ at: found >= 0 ? found : undefined });
        }
      }
      return;
    }
    const inSearch = event.target === search.current;
    const keys = {
      ArrowDown: () => move(1),
      ArrowUp: () => move(-1),
      Home: () => jump(false),
      End: () => jump(true),
      PageDown: () => move(10),
      PageUp: () => move(-10),
      Enter: () => choose(shown[active]),
      " ": inSearch ? undefined : () => choose(shown[active]),
      Escape: () => {
        event.stopPropagation();
        close();
      },
    };
    if (keys[key]) {
      event.preventDefault();
      keys[key]();
    } else if (key === "Tab") {
      close();
    } else if (!inSearch && printable(event)) {
      event.preventDefault();
      const found = typeAhead(key);
      if (found >= 0) setActive(found);
    }
  };
  const onSearch = (event) => {
    const next = event.target.value;
    setQuery(next);
    const word = next.trim().toLowerCase();
    setActive(
      firstEnabled(
        word ? options.filter((option) => matches(option, word)) : options,
      ),
    );
  };

  useEffect(() => {
    if (autoFocus) trigger.current?.focus();
  }, [autoFocus]);

  useLayoutEffect(() => {
    if (!open || sheet) return undefined;
    const place = (event) => {
      const button = trigger.current;
      const panel = menu.current;
      if (!button || !panel) return;
      if (event?.target instanceof Node && panel.contains(event.target)) return;
      const rect = button.getBoundingClientRect();
      if (natural.current === null) {
        panel.style.left = "0px";
        panel.style.width = "";
        natural.current = Math.ceil(panel.getBoundingClientRect().width);
      }
      const width = Math.max(rect.width, Math.min(natural.current, MAX_WIDTH));
      panel.style.width = `${width}px`;
      const height = Math.ceil(panel.getBoundingClientRect().height);
      const below = window.innerHeight - rect.bottom - GAP;
      const above = rect.top - GAP;
      const flip = height > below && above > below;
      const top = flip ? rect.top - GAP - height : rect.bottom + GAP;
      const left = Math.max(
        EDGE,
        Math.min(rect.left, window.innerWidth - width - EDGE),
      );
      panel.style.top = `${top}px`;
      panel.style.left = `${left}px`;
      const last = placedAt.current;
      if (
        last &&
        last.top === top &&
        last.left === left &&
        last.width === width &&
        last.flip === flip
      )
        return;
      placedAt.current = { top, left, width, flip };
      setPlacement(placedAt.current);
    };
    place();
    window.addEventListener("scroll", place, true);
    window.addEventListener("resize", place);
    return () => {
      window.removeEventListener("scroll", place, true);
      window.removeEventListener("resize", place);
    };
  }, [open, sheet, shown.length]);

  useEffect(() => {
    if (!open || sheet) return undefined;
    const onPress = (event) => {
      const button = trigger.current;
      if (
        wrapper.current?.contains(event.target) ||
        menu.current?.contains(event.target) ||
        [...(button?.labels || [])].some((element) =>
          element.contains(event.target),
        )
      )
        return;
      setOpen(false);
      setPlacement(null);
      const dialog = button?.closest("dialog[open]");
      if (dialog && event.target === dialog) {
        document.addEventListener("click", (click) => click.stopPropagation(), {
          capture: true,
          once: true,
        });
      }
    };
    document.addEventListener("mousedown", onPress);
    return () => document.removeEventListener("mousedown", onPress);
  }, [open, sheet]);

  const placed = sheet || Boolean(placement);
  useEffect(() => {
    if (!open || !placed) return undefined;
    const dialog = sheetRef.current;
    const button = trigger.current;
    const overflow = document.body.style.overflow;
    if (sheet && dialog && !dialog.open) {
      dialog.showModal();
      document.body.style.overflow = "hidden";
    }
    if (searchable) search.current?.focus();
    else if (sheet) dialog?.querySelector("[role=listbox]")?.focus();
    return () => {
      if (sheet && dialog?.open) dialog.close();
      document.body.style.overflow = overflow;
      if (sheet) button?.focus();
    };
  }, [open, sheet, placed, searchable]);

  const activeIndex = activeOption ? activeOption.index : -1;
  useEffect(() => {
    if (!open || activeIndex < 0) return;
    document
      .getElementById(`${id}-option-${activeIndex}`)
      ?.scrollIntoView?.({ block: "nearest" });
  }, [open, id, activeIndex]);

  const searchRow = searchable && (
    <div className={sheet ? selectSheetSearch : selectSearch}>
      <FiSearch aria-hidden="true" />
      <input
        ref={search}
        type="text"
        role="combobox"
        aria-label={`Search ${title || "options"}`}
        aria-expanded="true"
        aria-haspopup="listbox"
        aria-controls={listId}
        aria-activedescendant={
          activeOption ? optionId(activeOption) : undefined
        }
        aria-autocomplete="list"
        className="min-w-0 flex-1 border-0 bg-transparent p-0 text-spot-ink outline-none placeholder:text-spot-muted"
        placeholder="Type to search"
        autoComplete="off"
        spellCheck={false}
        value={query}
        onChange={onSearch}
        onKeyDown={onKeyDown}
      />
      {!sheet && (
        <kbd
          aria-hidden="true"
          className="rounded-md border border-[#23273524] px-1.5 py-px font-ui text-[11.5px]"
        >
          Esc
        </kbd>
      )}
    </div>
  );
  const rowHeight = sheet
    ? "min-h-11"
    : size === "mini"
      ? "min-h-8"
      : "min-h-10";
  const listFocus =
    sheet && !searchable
      ? {
          tabIndex: 0,
          onKeyDown,
          "aria-activedescendant": activeOption
            ? optionId(activeOption)
            : undefined,
        }
      : {};
  const list = (
    <ul
      id={listId}
      role="listbox"
      aria-label={title || undefined}
      {...listFocus}
      className="m-0 min-h-0 flex-1 list-none overflow-y-auto overscroll-contain p-0 outline-none [scrollbar-width:thin]"
    >
      {shown.length === 0 && (
        <li
          role="presentation"
          className="px-2.5 py-3 text-[13px] text-spot-muted"
        >
          Nothing matches
        </li>
      )}
      {shown.map((option, index) => {
        const selected = option.value === current;
        const heading =
          option.group && option.group !== shown[index - 1]?.group;
        return (
          <Fragment key={option.index}>
            {heading && (
              <li role="presentation" className={selectGroup}>
                {option.group}
              </li>
            )}
            <li
              id={optionId(option)}
              role="option"
              aria-selected={selected}
              aria-disabled={option.disabled || undefined}
              aria-labelledby={`${optionId(option)}-label${option.meta ? ` ${optionId(option)}-meta` : ""}`}
              aria-describedby={
                option.description ? `${optionId(option)}-note` : undefined
              }
              data-value={option.value}
              className={`${selectOption} ${option.description ? "min-h-[52px] py-1.5" : rowHeight} ${selected ? (index === active ? selectOptionChosenActive : selectOptionChosen) : index === active ? selectOptionActive : ""}`}
              onMouseMove={() => {
                if (!option.disabled && index !== active) setActive(index);
              }}
              onClick={() => choose(option)}
            >
              <span className="flex min-w-0 flex-col">
                <span id={`${optionId(option)}-label`} className="truncate">
                  {option.label}
                </span>
                {option.description && (
                  <span
                    id={`${optionId(option)}-note`}
                    className="text-[13px] font-normal text-spot-muted"
                  >
                    {option.description}
                  </span>
                )}
              </span>
              {(option.meta || selected) && (
                <span className="ml-auto flex shrink-0 items-center gap-2 whitespace-nowrap text-[13px] font-normal text-spot-muted">
                  {option.meta && (
                    <span id={`${optionId(option)}-meta`}>{option.meta}</span>
                  )}
                  {selected && (
                    <span
                      aria-hidden="true"
                      className="grid size-5 place-items-center rounded-full bg-spot-violet text-white"
                    >
                      <FiCheck className="size-3 stroke-[3]" />
                    </span>
                  )}
                </span>
              )}
            </li>
          </Fragment>
        );
      })}
    </ul>
  );
  const sizer = (
    <ul
      aria-hidden="true"
      className="invisible m-0 h-0 list-none overflow-hidden p-0"
    >
      {options.map((option) => (
        <li key={option.index} className={`${selectOption} h-0 min-h-0`}>
          <span className="whitespace-nowrap">{option.label}</span>
          <span className="ml-auto flex shrink-0 items-center gap-2 whitespace-nowrap text-[13px]">
            {option.meta}
            <span className="size-[15px]" />
          </span>
        </li>
      ))}
    </ul>
  );
  const footer = searchable && (
    <p className={selectFooter} role="status">
      {term
        ? `${shown.length} of ${options.length} match`
        : `${options.length} options`}
    </p>
  );

  const menuNode = sheet ? (
    <dialog
      ref={sheetRef}
      className={selectSheet}
      aria-label={title || undefined}
      onCancel={(event) => {
        event.preventDefault();
        close();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <div className="flex max-h-[85dvh] flex-col gap-2 px-3 pb-[max(16px,env(safe-area-inset-bottom))] pt-2.5">
        <span
          aria-hidden="true"
          className="h-1 w-10 shrink-0 self-center rounded-full bg-[#2327352e]"
        />
        <div className="flex shrink-0 items-center justify-between gap-3 px-1.5 py-1">
          <span className="text-[15px] font-medium">{title}</span>
          <button
            type="button"
            className="min-h-11 bg-transparent px-2 text-[13.5px] text-spot-violet"
            onClick={() => close()}
          >
            Done
          </button>
        </div>
        {searchRow}
        {list}
        {footer}
      </div>
    </dialog>
  ) : (
    <div
      ref={menu}
      className={`fixed z-50 ${selectMenu} ${placement ? (media("(prefers-reduced-motion: reduce)") ? "" : "animate-ov-lift") : "invisible"}`}
      style={
        placement
          ? {
              top: placement.top,
              left: placement.left,
              width: placement.width,
              maxHeight: MAX_HEIGHT,
              "--lift": placement.flip ? "-4px" : "4px",
            }
          : { top: 0, left: 0, maxWidth: MAX_WIDTH, maxHeight: MAX_HEIGHT }
      }
      onMouseDown={(event) => {
        if (event.target !== search.current) event.preventDefault();
      }}
    >
      {searchRow}
      {list}
      {footer}
      {sizer}
    </div>
  );
  const portal =
    open &&
    createPortal(
      <div className="dashboard-app">{menuNode}</div>,
      trigger.current?.closest("dialog[open]") ||
        document.getElementById("root") ||
        document.body,
    );

  return (
    <span
      ref={wrapper}
      className={`relative ${size === "form" ? "block w-full" : "inline-block max-w-full align-middle"} ${className}`}
    >
      <button
        ref={trigger}
        type="button"
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-controls={listId}
        aria-activedescendant={
          !sheet && !searchable && activeOption
            ? optionId(activeOption)
            : undefined
        }
        aria-label={ariaLabel}
        aria-labelledby={labelledBy}
        aria-describedby={describedBy}
        aria-invalid={invalid || (missing && !current) ? true : undefined}
        aria-required={required || undefined}
        value={current}
        disabled={disabled}
        className={selectTrigger[size]}
        onClick={() => (open ? close() : openMenu())}
        onKeyDown={onKeyDown}
        {...rest}
      >
        <span className="grid min-w-0 flex-1">
          <span
            className={`col-start-1 row-start-1 truncate ${chosen ? "" : "text-spot-muted"}`}
          >
            {chosen ? chosen.label : placeholder}
          </span>
          {fit === "longest" && size !== "form" && (
            <span
              aria-hidden="true"
              className="invisible col-start-1 row-start-1 h-0 overflow-hidden"
            >
              {options.map((option) => (
                <span key={option.index} className="block">
                  {option.label}
                </span>
              ))}
            </span>
          )}
        </span>
        <FiChevronDown
          aria-hidden="true"
          className={open ? "rotate-180" : ""}
        />
      </button>
      {required ? (
        <input
          className="pointer-events-none absolute inset-0 h-full w-full opacity-0"
          tabIndex={-1}
          aria-labelledby={labelledBy}
          aria-label={labelledBy ? undefined : label || ariaLabel}
          name={name}
          value={current}
          required
          autoComplete="off"
          onChange={() => {}}
          onInvalid={() => setMissing(true)}
          onKeyDown={(event) => {
            trigger.current?.focus();
            onKeyDown(event);
          }}
        />
      ) : (
        name && <input type="hidden" name={name} value={current} />
      )}
      {portal}
    </span>
  );
};
