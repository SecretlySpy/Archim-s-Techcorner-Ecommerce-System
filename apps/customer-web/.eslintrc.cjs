/** @type {import("eslint").Linter.Config} */
module.exports = {
  // Mark this as the lint boundary so parent-machine ESLint settings cannot change CI results.
  root: true,
  // Apply Next.js correctness, React, Hooks, accessibility, and Core Web Vitals rules.
  extends: ["next/core-web-vitals"],
  // Treat stale suppression comments as lint findings instead of silently hiding dead configuration.
  reportUnusedDisableDirectives: true,
};
