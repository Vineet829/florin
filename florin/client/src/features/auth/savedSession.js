import { decodeToken } from "react-jwt";

export const readSavedUser = (storage) => {
  try {
    const user = JSON.parse(storage.getItem("user"));
    if (user && typeof user === "object" && typeof user.username === "string") {
      const profile = withoutToken(user);
      if ("accessToken" in user) {
        storage.setItem("user", JSON.stringify(profile));
      }
      return profile;
    }
  } catch {
  }
  storage.removeItem("user");
  return null;
};

export const saveUser = (storage, user) =>
  storage.setItem("user", JSON.stringify(withoutToken(user)));

const withoutToken = ({ accessToken, ...profile }) => profile;

export const tokenRoles = (token) => {
  const roles = token ? decodeToken(token)?.roles : null;
  return Array.isArray(roles) ? roles : [];
};
