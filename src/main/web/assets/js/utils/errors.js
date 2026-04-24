export function getUserFacingErrorMessage(err, fallback = "Something went wrong. Please try again.") {
  const status = Number(err?.status || 0);
  if (status === 401) return "Your session expired. Please sign in again.";
  if (status >= 500) return fallback;
  const msg = String(err?.message || "").trim();
  if (!msg) return fallback;
  return msg;
}
