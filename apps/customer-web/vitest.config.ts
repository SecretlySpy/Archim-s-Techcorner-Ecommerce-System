import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // The current unit tests exercise validation and HTTP behavior without needing a browser emulator.
    environment: "node",
    // Keep discovery scoped to intentional TypeScript unit tests inside this application.
    include: ["src/**/*.test.ts"],
    // Prevent mocks from leaking state between tests and making outcomes order-dependent.
    clearMocks: true,
    restoreMocks: true,
  },
});
