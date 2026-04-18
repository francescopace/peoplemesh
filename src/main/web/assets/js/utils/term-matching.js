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
  if (smaller.size === 1) {
    const token = [...smaller][0];
    if (token.length < 4) return false;
    if ((smaller.size / bigger.size) < 0.5) return false;
    for (const biggerToken of bigger) {
      if (isExclusivePair(token, biggerToken)) return false;
    }
  }
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
    .replaceAll("c++", "cpp")
    .replaceAll("c#", "csharp")
    .replaceAll("f#", "fsharp")
    .replaceAll(".net", "dotnet")
    .replaceAll("node.js", "nodejs")
    .replaceAll(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .replaceAll(/\s+/g, " ");
}

function isExclusivePair(a, b) {
  if (!a || !b || a === b) return false;
  const pair = [a, b].sort().join("|");
  return pair === "go|golang"
    || pair === "java|javascript"
    || pair === "scala|scalability";
}
