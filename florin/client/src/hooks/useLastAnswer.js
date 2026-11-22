import { useState } from "react";

const useLastAnswer = (currentData, list) => {
  const [last, setLast] = useState({ data: currentData, list });
  if (currentData && currentData !== last.data) {
    const answered = { data: currentData, list };
    setLast(answered);
    return answered;
  }
  return last;
};

export default useLastAnswer;
