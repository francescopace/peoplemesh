Refactor this frontend codebase using a pragmatic, incremental approach that improves maintainability without over-engineering.

Architecture direction (mandatory):
- Use a layered + clean-lite approach: views/routes -> services -> api.
- Preferred flow: UI components/views -> services -> api layer.
- Do not introduce new abstraction layers (e.g., controllers, adapters) unless explicitly requested.
- Keep top-level folders stable: views (or routes/pages), components, services, api, utils, assets.
- Keep folder structure shallow; avoid creating many nested subfolders.
- Keep utils for shared stateless helpers (formatting/parsing/date manipulation), not business orchestration.
- Move stateless helper functions currently parked in components or views/services to utils where appropriate.

Core objectives:
1) Readability and cleanup
- Improve clarity, consistency, naming, and remove real duplication.
- Split overly long/complex components only when it improves comprehension.
- Flag components exceeding ~300 lines; split only when cohesion is genuinely low, not for line-count alone.
- Ensure consistent code style (naming conventions, file naming, import order).

2) Responsibility boundaries
- Pages/routes handle layout composition and data fetching orchestration only.
- Business logic lives in services (and stateless utils where appropriate), not in UI components.
- API calls live in the api layer; do not scatter fetch/axios calls across components.
- Prefer centralized API clients; do not mix raw fetch and wrapper calls in the same feature.
- Keep state management decisions in services/view-level orchestrators, not in leaf components.
- Enforce dependency direction:
  - components must not directly call API layer; they receive data from view/service orchestration
  - utils must not depend on components, views, services, or API layer
  - api layer must not depend on UI components, views, or services

3) Types and prop policy (mandatory)
- No cosmetic shape aliases (no 1:1 copies of API payloads without value), including plain JS schema wrappers.
- Introduce dedicated contracts/schemas only when needed for:
  - different component/view contract vs API response
  - form validation shapes
  - derived/computed view models
  - reusable component contracts
- Avoid dedicated mapping/transformer files for trivial transformations.
- Use dedicated transformers only when mapping is non-trivial and reused across features.

4) Error handling and security
- Handle errors consistently (error boundaries, toast notifications, or inline messages as appropriate).
- Do not log secrets, tokens, or sensitive values to the console.
- Validate user input at the form/component boundary before submission.
- Sanitize any user-generated content rendered in the DOM to prevent XSS.
- Do not expose internal API details or stack traces in user-facing error messages.

5) Performance and scope
- Reduce unnecessary re-renders and over-fetching.
- Apply caching/debouncing/memoization only where profiling justifies it; do not blanket-optimize.
- Do not introduce new state management libraries or caching layers.
- Keep refactor incremental; do not rewrite the whole codebase.
- Preserve behavior, routes, and external API contracts unless explicitly requested.

6) Styling and UI consistency
- Maintain consistent use of the existing styling approach (CSS modules, Tailwind, styled-components, or whatever is in place).
- Do not mix styling paradigms within the same feature.
- Extract repeated style patterns into shared components or utility classes where appropriate.

7) Accessibility
- Ensure semantic HTML elements are used appropriately.
- Maintain or improve ARIA attributes and keyboard navigation where applicable.
- Do not remove existing accessibility features.

8) Documentation
- Always update relevant documentation when behavior, configuration, or workflows change.

9) Testing and output
- Update/add tests where behavior changes.
- Prefer unit tests with mocking for service and utility logic.
- Use DOM/jsdom component-view tests for UI behavior; reserve E2E tests for critical user flows only.
- Do not introduce new test infrastructure dependencies without justification.
- Run relevant test suites and report results.
- Respect any existing coverage gates. Do not add new coverage excludes or reduce scanned files beyond what is already configured.
- Return:
  - key problems found (by impact),
  - files changed and rationale,
  - tests executed,
  - residual risks/trade-offs.
