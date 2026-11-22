import { useMemo } from "react";
import { useGetBusinessesQuery } from "./businessApiSlice";

export const REFUSALS = {
  EDIT_RECORDS: "Viewers can't change anything in this business",
  DELETE_RECORDS:
    "Only the owner and admins can delete anything in this business",
  EXPORT_ARCHIVE: "Only the owner and admins can download everything",
  MANAGE_BUSINESS:
    "Only the owner and admins can change the business's details and settings",
  MANAGE_TEAM: "Only the owner and admins can manage the team",
  VIEW_TEAM_ACTIVITY: "Only the owner and admins can see the team's activity",
  OWN_BUSINESS: "Only the owner can do that",
};

export const ROLE_SUMMARIES = {
  ADMIN: "Everything except handing the business over or deleting it.",
  MEMBER:
    "Creates, edits and emails documents, and works on clients, items and payments. Can't delete anything, download everything, or manage the business or its team.",
  VIEWER: "Sees everything and exports lists, but changes nothing.",
};

export const useBusiness = () => {
  const business = useGetBusinessesQuery().data?.businesses?.find(
    (candidate) => candidate.active,
  );
  return useMemo(() => {
    const permissions = business?.permissions;
    const can = (permission) =>
      !permissions || permissions.includes(permission);
    return {
      business,
      role: business?.role,
      can,
      reason: (permission) =>
        can(permission) ? undefined : REFUSALS[permission],
    };
  }, [business]);
};

export const RefusalNote = ({ id, reason }) =>
  reason ? (
    <span id={id} className="sr-only">
      {reason}
    </span>
  ) : null;
