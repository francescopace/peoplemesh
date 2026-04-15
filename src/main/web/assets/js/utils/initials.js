export function deriveInitialsFromName(name) {
  const cleaned = (name || "").trim();
  if (!cleaned) return "";
  const parts = cleaned.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return parts[0].slice(0, 2).toUpperCase();
}

export function deriveInitials(user) {
  const displayName = user?.display_name || user?.displayName || user?.name || "";
  const fromName = deriveInitialsFromName(displayName);
  if (fromName) return fromName;

  const provider = (user?.provider || "").trim();
  if (provider) return provider.slice(0, 2).toUpperCase();

  return "U";
}
