import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Navigate, Outlet } from "react-router-dom";
import { toast } from "react-toastify";
import { baseApiSlice } from "../api/baseApiSlice";
import { useGetBusinessesQuery } from "./businessApiSlice";
import { changeNoticed, setActiveBusiness } from "./businessSlice";

const Gate = () => {
  const dispatch = useDispatch();
  const activeId = useSelector((state) => state.business.activeId);
  const query = useGetBusinessesQuery();
  const active = query.data?.businesses?.find((business) => business.active);

  useEffect(() => {
    if (active && active._id !== activeId) {
      dispatch(setActiveBusiness(active._id));
    }
  }, [active, activeId, dispatch]);

  if (query.isError) return <Outlet />;
  if (!query.data) return null;
  if (!query.data.businesses.length) return <Navigate to="/welcome" replace />;
  if (active && active._id !== activeId) return null;
  return <Outlet />;
};

const BusinessGate = () => {
  const dispatch = useDispatch();
  const { activeId, changedElsewhere } = useSelector((state) => state.business);

  useEffect(() => {
    if (!changedElsewhere) return;
    dispatch(baseApiSlice.util.resetApiState());
    dispatch(changeNoticed());
    toast.info(
      activeId
        ? "You switched business in another tab, so this tab switched too."
        : "You're no longer part of that business.",
    );
  }, [activeId, changedElsewhere, dispatch]);

  if (changedElsewhere) return null;
  return <Gate key={activeId || "none"} />;
};

export default BusinessGate;
