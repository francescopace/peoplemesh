import { renderFooter } from "../footer.js";
import { renderBrand } from "../brand.js";

export function renderTermsOfService(container) {
  container.innerHTML = `
    <div class="legal-page">
      <header class="legal-topbar">
        <div class="legal-topbar-left">
          ${renderBrand({
            className: "app-brand-link",
            ariaLabel: "Go to PeopleMesh home",
            iconClass: "app-brand-icon",
            textClass: "app-brand-text",
          })}
        </div>
        <a href="#/" class="legal-back">
          <span class="material-symbols-outlined" style="font-size:18px;vertical-align:-3px">arrow_back</span>
          Back to Home
        </a>
      </header>
      <article class="legal-content container">
        <header class="legal-header">
          <h1 class="page-title">Terms of Service</h1>
          <p class="page-subtitle text-secondary">Last updated: April 2026</p>
        </header>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">handshake</span>
            <h2>1. Acceptance</h2>
          </div>
          <div class="legal-card-body">
            <p>By accessing or using PeopleMesh (&ldquo;the Platform&rdquo;), you agree to be bound by these Terms of Service. If you do not agree, do not use the Platform.</p>
            <p>PeopleMesh is currently in <strong>early access (MVP)</strong>. Features, APIs, and data formats may change. We will notify users of material changes.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">person</span>
            <h2>2. Eligibility</h2>
          </div>
          <div class="legal-card-body">
            <p>You must be at least 16 years of age to use the Platform. By using it, you represent that you meet this requirement.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">key</span>
            <h2>3. Account</h2>
          </div>
          <div class="legal-card-body">
            <p>You sign in through a third-party OAuth provider (Google or Microsoft). You are responsible for maintaining the security of your account with your provider. We do not store passwords.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">rule</span>
            <h2>4. Acceptable Use</h2>
          </div>
          <div class="legal-card-body">
            <p>You agree not to:</p>
            <ul>
              <li>Use the Platform for any illegal purpose.</li>
              <li>Harass, abuse, or threaten other users in any context (including matching).</li>
              <li>Submit false or misleading profile information.</li>
              <li>Attempt to circumvent security measures, rate limits, or access controls.</li>
              <li>Scrape, crawl, or collect data from the Platform without authorisation.</li>
              <li>Use the Platform to send unsolicited messages or spam.</li>
              <li>Post harmful, defamatory, or illegal content.</li>
              <li>Abuse the MCP (Model Context Protocol) interface to automate actions beyond normal profile management.</li>
            </ul>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">description</span>
            <h2>5. User Content</h2>
          </div>
          <div class="legal-card-body">
            <p>You retain ownership of content you submit (profile data, posts, comments, messages, uploaded CVs). By submitting content, you grant PeopleMesh a limited, non-exclusive licence to store, process, and display it as necessary to operate the Platform. We do not sell your content.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">apps</span>
            <h2>6. Platform Features</h2>
          </div>
          <div class="legal-card-body">
            <p>The Platform provides the following features, each subject to active consent (see our <a href="#/privacy_policy">Privacy Policy</a>):</p>
            <ul>
              <li><strong>Profiles:</strong> build your multi-dimensional profile via the web UI, AI assistant (MCP), CV import, or GitHub import. Changes are saved and applied immediately; imports are previewed before applying.</li>
              <li><strong>Matching:</strong> AI-powered matching connects you with people, jobs, and mesh nodes (events, projects, interest groups, and similar) based on semantic similarity and metadata scoring. Results are suggestions, not decisions.</li>
              <li><strong>Jobs:</strong> discover relevant job opportunities through AI-powered matching.</li>
              <li><strong>Mesh nodes:</strong> discover and interact with communities, events, projects, and interest groups through the mesh, subject to platform rules and your consents.</li>
            </ul>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">shield</span>
            <h2>7. Privacy</h2>
          </div>
          <div class="legal-card-body">
            <p>Your use of the Platform is governed by our <a href="#/privacy_policy">Privacy Policy</a>. By using the Platform, you acknowledge that you have read and understood it.</p>
            <p>You control your data through the <a href="#/privacy">Privacy Dashboard</a>, where you can export your data, restrict processing, manage consent scopes, and delete your account.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">group</span>
            <h2>8. Matching &amp; Contact</h2>
          </div>
          <div class="legal-card-body">
            <p>Match results are non-binding suggestions. Contact details (Slack handle, email) are visible in search and match results when you include them in your profile. We do not guarantee the accuracy or completeness of other users&rsquo; profiles. You interact with other users at your own discretion.</p>
            <p>Your country and city (when provided) are visible to other users in match results. To remove yourself from matching entirely, use the <strong>Restrict Processing</strong> control in the <a href="#/privacy">Privacy Dashboard</a>.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">copyright</span>
            <h2>9. Intellectual Property</h2>
          </div>
          <div class="legal-card-body">
            <p>PeopleMesh&rsquo;s matching engine, user interface, branding, and associated technology are protected by copyright and other intellectual property laws. The Platform&rsquo;s source code is available under the AGPL-3.0 licence as described in the project repository.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">logout</span>
            <h2>10. Termination</h2>
          </div>
          <div class="legal-card-body">
            <p>You may delete your account at any time via the <a href="#/privacy">Privacy Dashboard</a>. We may suspend or terminate your account for violations of these Terms. Upon deletion, your data is handled in accordance with our <a href="#/privacy_policy">Privacy Policy</a> (soft-deleted immediately, hard-deleted after 30 days).</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">warning</span>
            <h2>11. Disclaimer of Warranties</h2>
          </div>
          <div class="legal-card-body">
            <p>The Platform is provided &ldquo;as is&rdquo; without warranties of any kind, express or implied. We do not warrant that the Platform will be uninterrupted, error-free, or secure. As an MVP product, features may be added, changed, or removed without prior notice.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">block</span>
            <h2>12. Limitation of Liability</h2>
          </div>
          <div class="legal-card-body">
            <p>To the fullest extent permitted by law, PeopleMesh shall not be liable for any indirect, incidental, special, consequential, or punitive damages arising from your use of the Platform.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">edit_note</span>
            <h2>13. Changes</h2>
          </div>
          <div class="legal-card-body">
            <p>We may update these Terms from time to time. We will notify users of material changes. Continued use after changes constitutes acceptance.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">balance</span>
            <h2>14. Governing Law</h2>
          </div>
          <div class="legal-card-body">
            <p>These Terms are governed by the laws of the European Union and the applicable national laws of the jurisdiction where PeopleMesh operates.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">mail</span>
            <h2>15. Contact</h2>
          </div>
          <div class="legal-card-body">
            <p>For questions about these Terms: <strong>contact@peoplemesh.org</strong></p>
          </div>
        </div>
      </article>
      ${renderFooter({ extraClass: "app-footer" })}
    </div>
  `;
}
