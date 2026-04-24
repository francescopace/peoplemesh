import { renderFooter } from "../footer.js";
import { getPlatformInfo } from "../platform-info.js";
import { renderTopBar } from "../top-bar.js";

function esc(text) {
  if (!text) return "";
  const el = document.createElement("span");
  el.textContent = text;
  return el.innerHTML;
}

export async function renderPrivacyPolicy(container) {
  const info = await getPlatformInfo();
  const operatorLabel = info.organizationName
    ? `${esc(info.organizationName)} (&ldquo;the Operator&rdquo;)`
    : "The organisation or individual operating this instance (&ldquo;the Operator&rdquo;)";
  const contactLine = info.contactEmail
    ? `For data protection inquiries: <strong>${esc(info.contactEmail)}</strong>`
    : "For data protection inquiries, contact the Operator of this PeopleMesh instance.";
  const dpoLine = info.dpoName || info.dpoEmail
    ? `<p>Data Protection Officer: <strong>${esc(info.dpoName || "")}</strong>${info.dpoEmail ? ` &mdash; <strong>${esc(info.dpoEmail)}</strong>` : ""}</p>`
    : "";
  const dataLocationLine = info.dataLocation
    ? `Your data is stored in <strong>${esc(info.dataLocation)}</strong>.`
    : "Data storage location depends on where the Operator deploys the platform.";
  const legalTopBarHtml = renderTopBar({
    variant: "legal",
    organizationName: info.organizationName,
    rightHtml: `
      <a href="#/" class="legal-back">
        <span class="material-symbols-outlined" style="font-size:18px;vertical-align:-3px">arrow_back</span>
        Back to Home
      </a>
    `,
  });

  container.innerHTML = `
    <div class="legal-page">
      ${legalTopBarHtml}
      <article class="legal-content container">
        <header class="legal-header">
          <h1 class="page-title">Privacy Policy</h1>
          <p class="page-subtitle text-secondary">Last updated: April 2026</p>
        </header>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">shield</span>
            <h2>1. Data Controller</h2>
          </div>
          <div class="legal-card-body">
            <p>PeopleMesh is open-source software. ${operatorLabel} is the data controller for personal data processed through this platform. References to &ldquo;we&rdquo; and &ldquo;us&rdquo; in this policy refer to the Operator.</p>
            <p>The Operator is responsible for ensuring that their use of PeopleMesh complies with applicable data protection laws.</p>
            ${dpoLine}
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">database</span>
            <h2>2. Data We Collect</h2>
          </div>
          <div class="legal-card-body">
            <p>We collect the following categories of personal data:</p>
            <ul>
              <li><strong>Identity data:</strong> name, email address (provided via your OAuth login provider).</li>
              <li><strong>Profile data:</strong> professional roles, skills, industries, interests, hobbies, education, location (city, country, timezone), and other information you choose to share.</li>
              <li><strong>Communication data:</strong> interactions within the platform, including mesh node participation.</li>
              <li><strong>Technical data:</strong> hashed IP addresses (never stored in clear), session cookies, audit logs.</li>
              <li><strong>Imported data:</strong> CV or GitHub profile content when you use the import feature.</li>
            </ul>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">gavel</span>
            <h2>3. Lawful Basis for Processing</h2>
          </div>
          <div class="legal-card-body">
            <p>We process your personal data under the following legal bases (GDPR Art. 6):</p>
            <ul>
              <li><strong>Consent (Art. 6(1)(a)):</strong> for profile storage, matching, and embedding processing. You may withdraw consent at any time via the <a href="#/privacy">Privacy Dashboard</a>.</li>
              <li><strong>Legitimate interest (Art. 6(1)(f)):</strong> for security, fraud prevention, and platform integrity.</li>
              <li><strong>Legal obligation (Art. 6(1)(c)):</strong> for compliance with applicable laws and consent record-keeping.</li>
            </ul>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">tune</span>
            <h2>4. Consent Scopes &amp; Enforcement</h2>
          </div>
          <div class="legal-card-body">
            <p>We use granular, per-purpose consent. Each scope controls a specific processing activity, and <strong>revoking a consent immediately blocks the corresponding feature</strong>. You can manage all consents from the <a href="#/privacy">Privacy Dashboard</a>.</p>
            <table class="legal-table">
              <thead>
                <tr><th>Scope</th><th>Controls</th><th>Effect of revocation</th></tr>
              </thead>
              <tbody>
                <tr>
                  <td><code>professional_matching</code></td>
                  <td>Finding and being found by people, jobs, and mesh nodes</td>
                  <td>All matching features return empty results</td>
                </tr>
                <tr>
                  <td><code>embedding_processing</code></td>
                  <td>Generating semantic embeddings of your profile</td>
                  <td>Profile changes do not produce new embeddings, reducing match quality</td>
                </tr>
              </tbody>
            </table>
            <p>Re-granting a consent restores access immediately.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">settings</span>
            <h2>5. How We Use Your Data</h2>
          </div>
          <div class="legal-card-body">
            <ul>
              <li>To create and manage your account via OAuth providers.</li>
              <li>To match you with people, jobs, and mesh nodes based on your profile using semantic vector search and metadata scoring. This constitutes <strong>automated profiling</strong> under GDPR Art. 22; however, results are non-binding suggestions and you retain full control over whom to connect with.</li>
              <li>To generate semantic embeddings of your profile for matching (see Section 7). Embedding text excludes direct identifiers (name, email, city).</li>
              <li>To facilitate mesh node participation.</li>
              <li>To display your country and city in match results when present, so other users can assess geographic compatibility. Search visibility is controlled exclusively via the <strong>Restrict Processing</strong> control in the <a href="#/privacy">Privacy Dashboard</a> (GDPR Art. 18).</li>
              <li>To maintain security and pseudonymised audit logs.</li>
            </ul>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">lock</span>
            <h2>6. Security</h2>
          </div>
          <div class="legal-card-body">
            <p>Data is protected with TLS encryption in transit and infrastructure-level encryption at rest (database and storage). Access is controlled through OAuth2 authentication.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">share</span>
            <h2>7. Third-Party Data Processing</h2>
          </div>
          <div class="legal-card-body">
            <p>We share data with the following sub-processors for specific purposes:</p>
            <table class="legal-table">
              <thead>
                <tr><th>Sub-processor</th><th>Purpose</th><th>Data shared</th></tr>
              </thead>
              <tbody>
                <tr>
                  <td>OpenAI</td>
                  <td>Generate semantic embeddings for profile matching; structure CV content into profile fields; generate names for auto-discovered mesh nodes</td>
                  <td><strong>Embeddings:</strong> professional roles, skills, interests, hobbies, education, country (PII excluded: no name, email, city).<br><strong>CV structuring:</strong> full CV text as uploaded.<br><strong>Mesh node naming:</strong> aggregated, anonymous trait lists from clusters.</td>
                </tr>
                <tr>
                  <td>Docling (self-hosted)</td>
                  <td>Convert uploaded CV files to text</td>
                  <td>Uploaded CV file content (processed locally).</td>
                </tr>
                <tr>
                  <td>Google, Microsoft</td>
                  <td>User authentication (login)</td>
                  <td>OAuth subject identifier, email, display name, profile photo URL.</td>
                </tr>
                <tr>
                  <td>GitHub</td>
                  <td>Profile data import</td>
                  <td>OAuth subject data, profile fields (name, bio, skills, email, avatar, repository languages and labels) as authorised by the user.</td>
                </tr>
              </tbody>
            </table>
            <p>We do not sell your personal data. We do not share your data with advertisers. Embedding and LLM providers may be configured to use local/self-hosted alternatives, in which case no data leaves our infrastructure.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">verified_user</span>
            <h2>8. Your Rights (GDPR Art. 12&ndash;22)</h2>
          </div>
          <div class="legal-card-body">
            <p>You have the following rights, all exercisable via the <a href="#/privacy">Privacy Dashboard</a>:</p>
            <ul>
              <li><strong>Access (Art. 15):</strong> export all your data as JSON.</li>
              <li><strong>Rectification (Art. 16):</strong> update your profile at any time.</li>
              <li><strong>Erasure (Art. 17):</strong> delete your account and all associated data.</li>
              <li><strong>Restriction (Art. 18):</strong> restrict processing to exclude your profile from matching. This is the single, centralised control for search visibility.</li>
              <li><strong>Portability (Art. 20):</strong> download your data in a structured, machine-readable format (JSON).</li>
              <li><strong>Withdraw consent:</strong> revoke individual processing consents at any time. Revocation immediately blocks the corresponding feature (see Section 4).</li>
            </ul>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">schedule</span>
            <h2>9. Data Retention</h2>
          </div>
          <div class="legal-card-body">
            <ul>
              <li><strong>Active accounts:</strong> data is retained for as long as your account is active.</li>
              <li><strong>Inactive accounts:</strong> profiles inactive for more than 12 months are automatically deleted.</li>
              <li><strong>Deleted accounts:</strong> data is soft-deleted immediately and permanently removed after 30 days.</li>
              <li><strong>Consent records:</strong> grant/revoke timestamps, scope, and policy version are retained for compliance.</li>
              <li><strong>Audit logs:</strong> retained in pseudonymised form (SHA-256 hashed user ID and IP) for security purposes.</li>
              <li><strong>Pending imports:</strong> temporary data from CV/GitHub import expires per platform configuration.</li>
            </ul>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">cookie</span>
            <h2>10. Cookies</h2>
          </div>
          <div class="legal-card-body">
            <p>We use a single functional session cookie (<code>pm_session</code>) that is strictly necessary for authentication. It is <code>HttpOnly</code>, <code>SameSite=Lax</code>, and <code>Secure</code> in production. We do not use tracking cookies, analytics cookies, or advertising cookies.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">public</span>
            <h2>11. International Transfers</h2>
          </div>
          <div class="legal-card-body">
            <p>${dataLocationLine} When profile data is sent to external AI providers for embedding generation, CV structuring, or mesh node naming, it may be processed in a different jurisdiction. The Operator is responsible for ensuring appropriate safeguards (e.g. standard contractual clauses) are in place for any international transfers. When self-hosted models are configured, no data leaves the Operator&rsquo;s infrastructure.</p>
          </div>
        </div>

        <div class="legal-card">
          <div class="legal-card-header">
            <span class="material-symbols-outlined legal-card-icon">mail</span>
            <h2>12. Contact &amp; Complaints</h2>
          </div>
          <div class="legal-card-body">
            <p>${contactLine}</p>
            <p>You have the right to lodge a complaint with your national data protection authority.</p>
          </div>
        </div>
      </article>
      ${renderFooter({ extraClass: "app-footer" })}
    </div>
  `;
}
