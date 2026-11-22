import { useSelector } from "react-redux";
import { selectCurrentUserToken } from "../features/auth/authSlice";
import { tokenRoles } from "../features/auth/savedSession";

const useAuthUser = () => {
  const token = useSelector(selectCurrentUserToken);
  const roles = tokenRoles(token);
  const isAdmin = roles.includes("Admin");
  return { roles, isAdmin, accessRight: isAdmin ? "Admin" : "User" };
};

export default useAuthUser;
