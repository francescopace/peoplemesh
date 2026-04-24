export function termsMatch(a, b) {
  const aNorm = normalizeTerm(a);
  const bNorm = normalizeTerm(b);
  if (!aNorm || !bNorm) return false;
  if (aNorm === bNorm) return true;

  const aTokens = new Set(aNorm.split(" ").filter(Boolean));
  const bTokens = new Set(bNorm.split(" ").filter(Boolean));
  if (!aTokens.size || !bTokens.size) return false;

  const [smaller, bigger] = aTokens.size <= bTokens.size
    ? [aTokens, bTokens]
    : [bTokens, aTokens];
  for (const token of smaller) {
    if (!bigger.has(token)) return false;
  }
  return true;
}

export function normalizeTerm(value) {
  if (!value) return "";
  return value
    .toLowerCase()
    .trim()
    .replaceAll(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .replaceAll(/\s+/g, " ");
}
