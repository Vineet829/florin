import { Select } from "../../../components/workspace/Select";

const CurrencyChoice = ({ value, currencies = [], onChange }) => {
  if (currencies.length < 2) return null;
  return (
    <label className="min-w-0 max-[700px]:flex-[1_1_40%]">
      <span className="sr-only">Currency</span>
      <Select
        size="toolbar"
        className="max-[700px]:w-full"
        value={value}
        onChange={(currency) =>
          onChange(currency === currencies[0] ? "" : currency)
        }
      >
        {currencies.map((currency) => (
          <option key={currency} value={currency}>
            {currency}
          </option>
        ))}
      </Select>
    </label>
  );
};

export default CurrencyChoice;
