import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { apiDevProxy } from "./devProxy.ts";

// Server-only (no VITE_ prefix), so it is never exposed to browser code.
const devApiTarget = process.env.DEV_API_PROXY_TARGET;

export default defineConfig({
  plugins: [react()],

  server: {
    proxy: apiDevProxy(devApiTarget),
  },

  preview: {
    proxy: apiDevProxy(devApiTarget),
  },

  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.ts",
    execArgv:
      Number(process.versions.node.split(".")[0]) >= 25
        ? ["--no-webstorage"]
        : [],
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "json-summary", "lcov"],
      reportsDirectory: "./coverage",
      thresholds: {
        statements: 85,
        branches: 85,
        functions: 85,
        lines: 85,
      },
    },
  },
});
