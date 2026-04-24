import { defineConfig } from "vitest/config";

const frontendCoverageThresholds = {
  statements: Number(process.env.PM_FE_COVERAGE_STATEMENTS_MIN ?? 73),
  branches: Number(process.env.PM_FE_COVERAGE_BRANCHES_MIN ?? 58),
  functions: Number(process.env.PM_FE_COVERAGE_FUNCTIONS_MIN ?? 74),
  lines: Number(process.env.PM_FE_COVERAGE_LINES_MIN ?? 77),
};

export default defineConfig({
  test: {
    environment: "jsdom",
    include: ["__tests__/**/*.test.js"],
    globals: true,
    reporters: ["default"],
    coverage: {
      provider: "v8",
      reporter: ["text", "lcov"],
      reportsDirectory: "coverage",
      thresholds: frontendCoverageThresholds,
    },
  },
});
