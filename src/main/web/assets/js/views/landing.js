import { showLoginModal } from "../login-modal.js";
import { Auth } from "../auth.js";
import { renderFooter } from "../footer.js";
import { getOrganizationName } from "../platform-info.js";
import { bindTopBarUserMenu, renderTopBarUserMenu } from "../top-bar-user-menu.js";
import { getAuthenticatedTopBarNavItems, renderTopBar, renderTopBarNavLinks } from "../top-bar.js";

const EXAMPLE_QUERIES = [
  "Java developer with Kubernetes experience",
  "Community for data engineers in Europe",
  "Open roles in cloud architecture",
  "Events about AI and machine learning",
];

const SECTION_IDS = ["hero", "why"];

export async function renderLanding(container) {
  const isAuth = Auth.isAuthenticated();
  const user = isAuth ? Auth.getUser() : null;
  const organizationName = await getOrganizationName();
  const topBarUserMenuHtml = isAuth ? await renderTopBarUserMenu() : "";
  const topBarCenterHtml = isAuth
    ? renderTopBarNavLinks(getAuthenticatedTopBarNavItems(user))
    : "";
  const landingTopBarHtml = renderTopBar({
    variant: "landing",
    organizationName,
    centerHtml: topBarCenterHtml,
    rightHtml: `
      <div class="nav-actions">
        ${isAuth ? topBarUserMenuHtml : `<button class="nav-cta" id="nav-action-btn">Sign In</button>`}
      </div>
    `,
  });

  container.innerHTML = `
    ${landingTopBarHtml}

    <div class="landing-scroll" id="landing-scroll">

      <section class="hero landing-snap-section" id="section-hero">
        <div class="container">
          <div class="hero-content">
            <h1>The right match in your <em>mesh</em></h1>
            <div class="landing-search-wrap">
              <form class="search-input-wrap" id="landing-search-form">
                <input type="text" class="search-input" id="landing-search-input"
                  placeholder="Describe what you're looking for in your organization\u2026"
                  autocomplete="off" maxlength="500">
                <button type="submit" class="search-btn">
                  <span class="material-symbols-outlined">search</span>
                </button>
              </form>
            </div>
            <div class="landing-examples" id="landing-examples"></div>
          </div>
        </div>
        <button class="section-nav-arrow" id="scroll-to-why" aria-label="Learn more">
          <i class="fa-solid fa-chevron-down"></i>
        </button>
      </section>

      <section class="landing-why landing-snap-section" id="section-why">
        <div class="container">
          <div class="section-header">
            <h2>Why PeopleMesh</h2>
          </div>
          <div class="features-grid landing-why-grid">
            <div class="feature-card">
              <div class="feature-icon feature-icon-primary"><i class="fa-solid fa-brain" aria-hidden="true"></i></div>
              <h3>Search Like You Think</h3>
              <p>No filters, no keywords. Just describe what you need. PeopleMesh understands context, skills, roles, and intent across people, jobs, communities, and events.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon feature-icon-accent"><i class="fa-solid fa-lock" aria-hidden="true"></i></div>
              <h3>Your Data, Your Rules</h3>
              <p>Full GDPR compliance, granular consent, and zero data selling. You decide exactly what gets shared and with whom.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon feature-icon-info"><i class="fa-solid fa-building" aria-hidden="true"></i></div>
              <h3>Enterprise Integrated</h3>
              <p>Connects with your company tools like Workday, Slack, SSO. Discovery fits naturally into the way your organization already works.</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon feature-icon-success"><i class="fa-solid fa-plug" aria-hidden="true"></i></div>
              <h3>MCP Ready</h3>
              <p>Use PeopleMesh from the web, or directly from ChatGPT and Claude via MCP. Discovery meets you where you work.</p>
            </div>
          </div>
        </div>
        ${renderFooter({ extraClass: "app-footer" })}
      </section>

    </div>

    <div class="section-dots" id="section-dots"></div>
  `;

  const examplesWrap = container.querySelector("#landing-examples");
  EXAMPLE_QUERIES.forEach((query) => {
    const chip = document.createElement("button");
    chip.className = "landing-example-chip";
    chip.type = "button";
    chip.dataset.query = query;
    chip.textContent = query;
    examplesWrap.appendChild(chip);
  });

  const sectionDotsWrap = container.querySelector("#section-dots");
  SECTION_IDS.forEach((id) => {
    const dot = document.createElement("button");
    dot.className = "section-dot";
    dot.type = "button";
    dot.dataset.target = id;
    dot.setAttribute("aria-label", `Go to ${id}`);
    sectionDotsWrap.appendChild(dot);
  });

  /* === Cookie banner === */
  if (!localStorage.getItem("pm_cookie_ack")) {
    const banner = document.createElement("div");
    banner.className = "cookie-banner";
    banner.innerHTML = `
      <div class="cookie-banner-inner">
        <p>This site uses a single session cookie for authentication. No tracking, no ads, no profiling.
        <a href="#/privacy_policy">Learn more</a></p>
        <button class="btn btn-sm" id="cookie-ack-btn">OK</button>
      </div>
    `;
    container.appendChild(banner);
    banner.querySelector("#cookie-ack-btn").addEventListener("click", () => {
      localStorage.setItem("pm_cookie_ack", "1");
      banner.remove();
    });
  }

  const input = container.querySelector("#landing-search-input");
  const form = container.querySelector("#landing-search-form");

  function handleSearch(query) {
    if (!query) return;
    if (Auth.isAuthenticated()) {
      window.location.hash = `#/search?q=${encodeURIComponent(query)}`;
    } else {
      sessionStorage.setItem("pm_pending_search", query);
      showLoginModal();
    }
  }

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    handleSearch(input.value.trim());
  });

  container.querySelectorAll(".landing-example-chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      input.value = chip.dataset.query;
      handleSearch(chip.dataset.query);
    });
  });

  if (isAuth) {
    bindTopBarUserMenu(container);
  } else {
    container.querySelector("#nav-action-btn").addEventListener("click", () => showLoginModal());
  }

  const scrollContainer = container.querySelector("#landing-scroll");
  const nav = container.querySelector("#landing-nav");
  const dots = container.querySelectorAll(".section-dot");

  function scrollToSection(id) {
    const target = container.querySelector(`#section-${id}`);
    if (target) target.scrollIntoView({ behavior: "smooth" });
  }

  container.querySelector("#scroll-to-why").addEventListener("click", () => scrollToSection("why"));

  container.querySelectorAll("[data-target]").forEach((btn) => {
    btn.addEventListener("click", (e) => {
      if (btn.id === "scroll-to-why") return;
      e.preventDefault();
      scrollToSection(btn.dataset.target);
    });
  });

  function updateActiveDot() {
    const scrollTop = scrollContainer.scrollTop;
    const viewH = scrollContainer.clientHeight;
    let activeIdx = 0;

    SECTION_IDS.forEach((id, i) => {
      const el = container.querySelector(`#section-${id}`);
      if (el && el.offsetTop <= scrollTop + viewH * 0.4) activeIdx = i;
    });

    dots.forEach((d, i) => d.classList.toggle("active", i === activeIdx));
    nav.classList.toggle("scrolled", scrollTop > 10);
  }

  scrollContainer.addEventListener("scroll", updateActiveDot, { passive: true });
  updateActiveDot();

  input.focus();
}
