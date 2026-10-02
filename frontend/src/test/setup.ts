import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";

// Keep device-level preferences (such as the sidebar state) from leaking between tests.
// Proxy and routing tests run in the Node environment, which has no window.
afterEach(() => {
  if (typeof window !== "undefined") window.localStorage.clear();
});
