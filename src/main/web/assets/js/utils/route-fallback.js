export function renderRouteFallback(container, message) {
  container.innerHTML = "";

  const wrap = document.createElement("div");
  wrap.className = "empty-state";
  wrap.style.minHeight = "100dvh";

  const text = document.createElement("p");
  text.textContent = message;
  wrap.appendChild(text);

  const link = document.createElement("a");
  link.href = "#/";
  link.className = "btn btn-primary";
  link.textContent = "Go Home";
  wrap.appendChild(link);

  container.appendChild(wrap);
}
