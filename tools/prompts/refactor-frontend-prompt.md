Refactor this frontend codebase using a pragmatic, incremental approach that improves maintainability without over-engineering.

Architecture direction (mandatory):
- Use a layered + clean-lite approach: pages/routes -> hooks/services -> API clients/stores.
- Preferred flow: UI components -> hooks/services -> API layer.
- Do not introduce new abstraction layers (e.g., controllers, adapters) unless explicitly requested.
- Keep top-level folders stable: pages (or routes), components, hooks, services, api, types, utils, assets.
- Keep folder structure shallow; avoid creating many nested subfolders.
- Keep utils for shared stateless helpers (formatting/parsing/date manipulation), not business orchestration.
- Move stateless helper functions currently parked in components or hooks to utils where appropriate.

Core objectives:
1) Readability and cleanup
- Improve clarity, consistency, naming, and remove real duplication.
- Split overly long/complex components only when it improves comprehension.
- Flag components exceeding ~300 lines; split only when cohesion is genuinely low, not for line-count alone.
- Ensure consistent code style (naming conventions, file naming, import order).

2) Responsibility boundaries
- Pages/routes handle layout composition and data fetching orchestration only.
- Business logic lives in hooks or services, not in UI components.
- API calls live in the api layer; do not scatter fetch/axios calls across components.
- Prefer centralized API clients; do not mix raw fetch and wrapper calls in the same feature.
- Keep state management decisions in hooks/services, not in leaf components.
- Enforce dependency direction:
  - components must not directly call API layer; they receive data via props or hooks
  - utils must not depend on components, hooks, or API layer
  - api layer must not depend on UI components or hooks

3) Types and prop policy (mandatory)
- No cosmetic type aliases (no 1:1 clones of API response types without value).
- Introduce dedicated types/interfaces only when needed for:
  - different component contract vs API response
  - form validation shapes
  - derived/computed view models
  - prop contracts for reusable components
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
- Apply memoization (React.memo, useMemo, useCallback) only where profiling justifies it; do not blanket-memoize.
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
- Prefer unit tests (e.g., Vitest, Jest) with mocking for hook and service logic.
- Use integration/component tests (e.g., Testing Library) for component behavior; reserve E2E tests (e.g., Playwright, Cypress) for critical user flows only.
- Do not introduce new test infrastructure dependencies without justification.
- Run relevant test suites and report results.
- Respect any existing coverage gates. Do not add new coverage excludes or reduce scanned files beyond what is already configured.
- Return:
  - key problems found (by impact),
  - files changed and rationale,
  - tests executed,
  - residual risks/trade-offs.
