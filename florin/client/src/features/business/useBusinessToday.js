import { parseDate } from "../../utils/dates";
import { useBusiness } from "./permissions";

export const todayIn = (timeZone, now = new Date()) => {
  let parts;
  try {
    parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: timeZone || undefined,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(now);
  } catch {
    return todayIn(undefined, now);
  }
  const part = (type) => parts.find((entry) => entry.type === type).value;
  return parseDate(`${part("year")}-${part("month")}-${part("day")}`);
};

const useBusinessToday = () => todayIn(useBusiness().business?.timeZone);

export default useBusinessToday;
