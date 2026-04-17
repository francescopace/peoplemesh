export function renderFooter({ extraClass = "" } = {}) {
  const year = new Date().getFullYear();
  const footerClass = extraClass ? `landing-footer ${extraClass}` : "landing-footer";

  return `
    <footer class="${footerClass}">
      <div class="container">
        <div class="footer-content">
          <div class="footer-brand-block">
            <div class="footer-brand-name">PeopleMesh &copy; ${year}</div>
          </div>
          <div class="footer-links">
            <a href="https://github.com/francescopace/peoplemesh" target="_blank" rel="noreferrer">GitHub</a>
            <a href="https://github.com/francescopace/peoplemesh/issues" target="_blank" rel="noreferrer">Issues</a>
            <a href="https://github.com/francescopace/peoplemesh/discussions" target="_blank" rel="noreferrer">Discussions</a>
            <a href="#/privacy_policy">Privacy Policy</a>
            <a href="#/terms_of_service">Terms of Service</a>
          </div>
        </div>
      </div>
    </footer>
  `;
}
