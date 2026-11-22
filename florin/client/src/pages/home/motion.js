import { useEffect, useRef, useState } from "react";
import usePageVisible from "../../hooks/usePageVisible";

export const usePrefersReducedMotion = () => {
  const [reduced] = useState(
    () =>
      window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false,
  );
  return reduced;
};

export const useReveal = (motion) => {
  const ref = useRef(null);
  const [shown, setShown] = useState(
    () => !motion || typeof IntersectionObserver === "undefined",
  );
  useEffect(() => {
    if (shown) return undefined;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return;
        setShown(true);
        observer.disconnect();
      },
      { rootMargin: "0px 0px -10% 0px" },
    );
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [shown]);
  return [
    ref,
    !motion
      ? ""
      : shown
        ? "animate-ov-rise print:animate-none"
        : "opacity-0 print:opacity-100",
  ];
};

export const useOnScreen = (ref) => {
  const [onScreen, setOnScreen] = useState(
    () => typeof IntersectionObserver === "undefined",
  );
  useEffect(() => {
    if (typeof IntersectionObserver === "undefined") return undefined;
    const observer = new IntersectionObserver(([entry]) =>
      setOnScreen(entry.isIntersecting),
    );
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [ref]);
  return onScreen;
};

export const useRunning = (ref, motion) => {
  const onScreen = useOnScreen(ref);
  const visible = usePageVisible();
  return motion && onScreen && visible;
};

export const useInterval = (tick, ms, running) => {
  const latest = useRef(tick);
  useEffect(() => {
    latest.current = tick;
  });
  useEffect(() => {
    if (!running) return undefined;
    const id = setInterval(() => latest.current(), ms);
    return () => clearInterval(id);
  }, [ms, running]);
};
