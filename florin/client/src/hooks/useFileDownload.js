import { toast } from "react-toastify";
import { errorMessage } from "../components/workspace/Workspace";
import { saveFile } from "../features/documents/useDocumentPdf";

const useFileDownload = (useFileMutation) => {
  const [fetchFile, { isLoading }] = useFileMutation();
  const download = async (arg, fileName) => {
    try {
      saveFile(await fetchFile(arg).unwrap(), fileName);
    } catch (error) {
      toast.error(errorMessage(error, "The file couldn't be prepared."));
    }
  };
  return [download, isLoading];
};

export default useFileDownload;
