export const printsOnPdfs = (logo) =>
  !logo?.trim() || /^\/uploads\/[^/]+\.(png|jpe?g|gif)$/i.test(logo.trim());

const showsOnPages = (logo, page) => {
  try {
    const url = new URL(logo, page.href);
    return url.protocol === "data:" || url.host === page.host;
  } catch {
    return false;
  }
};

export const logoHint = (logo, page = window.location) => {
  if (printsOnPdfs(logo)) return undefined;
  return showsOnPages(logo.trim(), page)
    ? "Your PDFs leave this logo out. Upload it below as a PNG, JPEG or GIF to print it on them too."
    : "Logos from other sites don't show on your documents, share links or PDFs. Upload it below as a PNG, JPEG or GIF.";
};
