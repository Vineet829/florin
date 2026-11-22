import { toast } from "react-toastify";
import { errorMessage } from "../../../components/workspace/Workspace";
import { EmailDialog } from "./EmailDocumentDialog";
import {
  useGetReminderDraftQuery,
  useSendReminderMutation,
} from "../documentsApiSlice";

const ReminderDialog = ({ documentId, open, onClose }) => {
  const draft = useGetReminderDraftQuery(documentId, { skip: !open });
  const [send, { isLoading: sending }] = useSendReminderMutation();
  const sendReminder = async (form) => {
    try {
      const result = await send({ id: documentId, ...form }).unwrap();
      toast.success(result.message || "Reminder sent successfully.");
      onClose();
    } catch (error) {
      toast.error(errorMessage(error, "The reminder couldn't be sent."));
    }
  };
  return (
    <EmailDialog
      title="Send reminder"
      open={open}
      onClose={onClose}
      draft={draft}
      sending={sending}
      onSend={sendReminder}
    />
  );
};

export default ReminderDialog;
