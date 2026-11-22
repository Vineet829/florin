import { useEffect, useState } from "react";
import { API_BASE } from "../../api/apiBase";
import { useGetSignInProvidersQuery } from "../authApiSlice";
import { GoogleIcon } from "./authCommon";
import { auth } from "./authForm";

const ProviderButtons = () => {
  const { data, isSuccess } = useGetSignInProvidersQuery();
  const [leaving, setLeaving] = useState(null);

  useEffect(() => {
    const onPageShow = (event) => {
      if (event.persisted) setLeaving(null);
    };
    window.addEventListener("pageshow", onPageShow);
    return () => window.removeEventListener("pageshow", onPageShow);
  }, []);

  if (!isSuccess || !data?.providers?.length) return null;

  return data.providers.map(({ id, name }) => (
    <button
      key={id}
      type="button"
      className={`${auth.ghost} ${auth.tight}`}
      onClick={() => {
        setLeaving(id);
        window.location.assign(`${API_BASE}/auth/oauth2/authorization/${id}`);
      }}
      disabled={leaving === id}
    >
      {id === "google" && <GoogleIcon />}
      {name}
    </button>
  ));
};

export default ProviderButtons;
