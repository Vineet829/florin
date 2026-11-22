export const fetchFile = async (baseQuery, url) => {
  const result = await baseQuery({
    url,
    responseHandler: (response) =>
      response.ok ? response.blob() : response.json(),
  });
  if (result.error) return { error: result.error };
  return { data: URL.createObjectURL(result.data) };
};
