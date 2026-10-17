import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { lazy, Suspense } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PAGE_RELOAD_KEY } from "../utils/pageLoadRecovery";
import { PageErrorBoundary } from "./PageErrorBoundary";

const DOWNLOAD_ERROR = "Failed to fetch dynamically imported module: https://app.example/assets/BudgetPage-old.js";

/** A lazy page whose code can never be downloaded, as after a deployment replaced it. */
function missingPage() {
  return lazy(() => Promise.reject(new TypeError(DOWNLOAD_ERROR)));
}

function BrokenPage(): never {
  throw new TypeError("Cannot read properties of undefined (reading 'map')");
}

function renderBoundary(page: React.ReactNode, reload = vi.fn()) {
  render(
    <PageErrorBoundary reload={reload}>
      <Suspense fallback={<p>Loading page…</p>}>{page}</Suspense>
    </PageErrorBoundary>,
  );
  return reload;
}

describe("PageErrorBoundary", () => {
  beforeEach(() => {
    // React reports every caught render error to the console; the tests assert the UI instead.
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders the page when nothing fails", () => {
    const reload = renderBoundary(<h1>Budgets</h1>);

    expect(screen.getByRole("heading", { name: "Budgets" })).toBeInTheDocument();
    expect(reload).not.toHaveBeenCalled();
  });

  it("reloads once to pick up a new version when a page's code cannot be downloaded", async () => {
    const MissingPage = missingPage();
    const reload = renderBoundary(<MissingPage />);

    expect(await screen.findByRole("status")).toHaveTextContent("Loading the latest version…");
    expect(reload).toHaveBeenCalledTimes(1);
    expect(window.sessionStorage.getItem(PAGE_RELOAD_KEY)).not.toBeNull();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows the message instead of reloading again when the page still fails after a reload", async () => {
    window.sessionStorage.setItem(PAGE_RELOAD_KEY, String(Date.now()));
    const MissingPage = missingPage();
    const reload = renderBoundary(<MissingPage />);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("This page couldn't load");
    expect(alert).toHaveTextContent("Your saved data is not affected.");
    expect(reload).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "Reload" }));
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("shows the message without reloading for errors that are not failed downloads", async () => {
    const reload = renderBoundary(<BrokenPage />);

    expect(await screen.findByRole("heading", { name: "This page couldn't load" })).toBeInTheDocument();
    expect(reload).not.toHaveBeenCalled();
    expect(window.sessionStorage.getItem(PAGE_RELOAD_KEY)).toBeNull();
  });

  it("reloads the whole page from the Reload button by default", async () => {
    // jsdom cannot navigate, so its reload is replaced to observe the call.
    const reload = vi.fn();
    vi.stubGlobal("location", { ...window.location, reload });
    try {
      render(
        <PageErrorBoundary>
          <BrokenPage />
        </PageErrorBoundary>,
      );

      await userEvent.click(await screen.findByRole("button", { name: "Reload" }));
      expect(reload).toHaveBeenCalledTimes(1);
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
