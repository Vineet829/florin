import { useCallback } from "react";
import { useDispatch } from "react-redux";
import { useNavigate } from "react-router-dom";
import { baseApiSlice } from "../api/baseApiSlice";
import { setActiveBusiness } from "./businessSlice";

const useEnterBusiness = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  return useCallback(
    (businessId) => {
      dispatch(setActiveBusiness(businessId));
      dispatch(baseApiSlice.util.resetApiState());
      navigate("/dashboard");
    },
    [dispatch, navigate],
  );
};

export default useEnterBusiness;
