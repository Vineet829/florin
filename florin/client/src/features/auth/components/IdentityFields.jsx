import { Field } from "../../../components/workspace/Workspace";
import { useGetTwoFactorQuery } from "../../settings/securityApiSlice";

export const emptyIdentity = { password: "", code: "" };

const IdentityFields = ({ values, onChange, disabled }) => {
  const twoFactor = useGetTwoFactorQuery().data?.enabled;
  const edit = (key) => (event) =>
    onChange({ ...values, [key]: event.target.value });
  return (
    <div className="mt-5 flex flex-col gap-4">
      <Field
        label="Your password"
        type="password"
        autoComplete="current-password"
        maxLength={128}
        required
        value={values.password}
        onChange={edit("password")}
        disabled={disabled}
      />
      {twoFactor && (
        <Field
          label="Two-factor code"
          hint="From your authenticator app, or one of your recovery codes."
          autoComplete="one-time-code"
          maxLength={20}
          required
          value={values.code}
          onChange={edit("code")}
          disabled={disabled}
        />
      )}
    </div>
  );
};

export default IdentityFields;
