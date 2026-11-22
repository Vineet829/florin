const KEY = "pendingInvite";
const OPEN_FOR = 7 * 24 * 60 * 60 * 1000;

export const rememberInvite = (token) => {
  try {
    localStorage.setItem(KEY, JSON.stringify({ token, savedAt: Date.now() }));
  } catch {
  }
};

export const forgetInvite = () => {
  try {
    localStorage.removeItem(KEY);
  } catch {
  }
};

export const pendingInvitePath = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY));
    if (
      typeof saved?.token === "string" &&
      Date.now() - saved.savedAt < OPEN_FOR
    ) {
      return `/invite/${encodeURIComponent(saved.token)}`;
    }
  } catch {
  }
  return null;
};
