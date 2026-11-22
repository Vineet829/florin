import { Select } from "../../../components/workspace/Select";

const CurrencyFilter = ({ value, currencies = [], onChange }) => {
  if (currencies.length < 2 && !value) return null;
  const options =
    currencies.includes(value) || !value ? currencies : [...currencies, value];
  return (
    <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
      <span className="sr-only">Currency</span>
      <Select
        size="toolbar"
        className="max-[700px]:w-full"
        value={value}
        onChange={onChange}
      >
        <option value="">All currencies</option>
        {options.map((currency) => (
          <option key={currency} value={currency}>
            {currency}
          </option>
        ))}
      </Select>
    </label>
  );
};

export default CurrencyFilter;
