import { useEffect, useRef } from "react";
import { useLocation, useNavigationType } from "react-router-dom";

const ScrollToTop = () => {
  const { pathname, hash } = useLocation();
  const navigation = useNavigationType();
  const page = useRef(pathname);
  useEffect(() => {
    if (page.current === pathname) return;
    page.current = pathname;
    if (navigation !== "POP" && !hash) window.scrollTo(0, 0);
  }, [pathname, hash, navigation]);
  return null;
};

export default ScrollToTop;
