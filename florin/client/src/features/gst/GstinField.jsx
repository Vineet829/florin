import { Field } from "../../components/workspace/Workspace";
import { gstinProblem, isValidGstin, normalizeGstin } from "./gstin";
import { useGetGstStatesQuery } from "./gstApiSlice";

const GstinField = ({ value, ...props }) => {
  const states = useGetGstStatesQuery().data?.states;
  const gstin = normalizeGstin(value);
  const problem = gstinProblem(value);
  const state =
    isValidGstin(gstin) &&
    states?.find((entry) => entry.code === gstin.slice(0, 2));
  return (
    <Field
      label="GSTIN"
      autoComplete="off"
      maxLength={15}
      value={value}
      error={problem || undefined}
      hint={
        state
          ? `Registered in ${state.name} (${state.code}).`
          : "The client's Indian GST number, printed on your documents."
      }
      {...props}
    />
  );
};

export default GstinField;
