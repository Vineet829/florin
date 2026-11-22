import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";

const useListState = (read, write) => {
  const [params, setParams] = useSearchParams();
  const state = useMemo(() => read(params), [params, read]);
  const update = useCallback(
    (changes) =>
      setParams((current) => write({ ...read(current), ...changes }), {
        replace: true,
      }),
    [setParams, read, write],
  );
  return [state, update];
};

export default useListState;
