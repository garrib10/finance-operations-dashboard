import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";

// jsdom has no modal <dialog> support. This stand-in only tracks the open state and the
// close event; real focus containment and inertness are verified in a browser.
if (typeof HTMLDialogElement !== "undefined" && typeof HTMLDialogElement.prototype.showModal !== "function") {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    if (!this.open) return;
    this.open = false;
    this.dispatchEvent(new Event("close"));
  };
}

// Keep device-level preferences (such as the sidebar state) from leaking between tests.
// Proxy and routing tests run in the Node environment, which has no window.
afterEach(() => {
  if (typeof window !== "undefined") window.localStorage.clear();
});
