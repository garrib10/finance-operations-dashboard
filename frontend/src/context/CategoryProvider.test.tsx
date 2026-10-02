vi.mock("./AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../services/categoryService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/categoryService")>()),
  getCategories: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
}));

import { useEffect } from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "./AuthContext";
import { useCategories } from "./CategoryContext";
import { CategoryProvider } from "./CategoryProvider";
import * as categoryService from "../services/categoryService";
import { accountContext, accountUser, deferred } from "../test/accountFixtures";
import { category, groceries, petCare } from "../test/categoryFixtures";
import type { CategoryContextValue } from "./CategoryContext";
import type { CategoryResponse } from "../types/category";

let latest: CategoryContextValue;

function capture(value: CategoryContextValue) {
  latest = value;
}

function Probe() {
  const value = useCategories();
  useEffect(() => capture(value));
  return (
    <div>
      <p data-testid="status">{value.status}</p>
      <p data-testid="names">{value.categories.map((item) => item.name).join(",")}</p>
      <p data-testid="load-error">{value.loadError}</p>
      <p data-testid="refresh-error">{value.refreshError}</p>
    </div>
  );
}

const otherUser = { ...accountUser, id: 2, email: "other@fintrack.dev" };
const theirs = category({ id: 90, name: "Their Secret Category" });

function signIn(user: typeof accountUser | null) {
  vi.mocked(useAuth).mockReturnValue(user ? accountContext(user) : { ...accountContext(accountUser), user: null, isAuthenticated: false });
}

describe("CategoryProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    signIn(accountUser);
    vi.mocked(categoryService.getCategories).mockResolvedValue([groceries, petCare]);
  });

  it("loads once on first use and shares the list", async () => {
    render(<CategoryProvider><Probe /><Probe /></CategoryProvider>);

    expect(screen.getAllByTestId("status")[0]).toHaveTextContent("loading");
    await waitFor(() => expect(screen.getAllByTestId("status")[0]).toHaveTextContent("ready"));
    expect(screen.getAllByTestId("names")[1]).toHaveTextContent("Groceries,Pet Care");
    expect(categoryService.getCategories).toHaveBeenCalledTimes(1);
  });

  it("does not request categories until a view needs them", () => {
    render(<CategoryProvider><p>No categories here</p></CategoryProvider>);

    expect(categoryService.getCategories).not.toHaveBeenCalled();
  });

  it("reports a first-load failure and recovers on retry", async () => {
    vi.mocked(categoryService.getCategories).mockRejectedValueOnce(new Error("network"));
    render(<CategoryProvider><Probe /></CategoryProvider>);

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("error"));
    expect(screen.getByTestId("load-error")).toHaveTextContent("Categories could not be loaded.");

    await act(async () => {
      expect(await latest.reload()).toEqual([groceries, petCare]);
    });
    expect(screen.getByTestId("status")).toHaveTextContent("ready");
    expect(screen.getByTestId("load-error")).toBeEmptyDOMElement();
  });

  it("keeps the last list and warns when a later refresh fails", async () => {
    render(<CategoryProvider><Probe /></CategoryProvider>);
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("ready"));

    vi.mocked(categoryService.getCategories).mockRejectedValueOnce(new Error("network"));
    await act(async () => {
      expect(await latest.reload()).toBeNull();
    });

    expect(screen.getByTestId("status")).toHaveTextContent("ready");
    expect(screen.getByTestId("names")).toHaveTextContent("Groceries,Pet Care");
    expect(screen.getByTestId("refresh-error")).toHaveTextContent("Category options could not be refreshed.");

    await act(async () => {
      await latest.reload();
    });
    expect(screen.getByTestId("refresh-error")).toBeEmptyDOMElement();
  });

  it("refreshes after a rename and icon change, returning the server result", async () => {
    render(<CategoryProvider><Probe /></CategoryProvider>);
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("ready"));
    const created = category({ id: 50, name: "Gym", iconKey: "dumbbell" });
    const renamed = { ...petCare, name: "Pets", iconKey: "heart-pulse" };
    vi.mocked(categoryService.updateCategory).mockResolvedValue(renamed);
    // A category created elsewhere (a transaction save) shows up on the same refresh.
    vi.mocked(categoryService.getCategories).mockResolvedValueOnce([created, groceries, renamed]);

    await act(async () => {
      expect(await latest.updateCategory(petCare.id, { name: "Pets", budgetEnabled: true, iconKey: "heart-pulse" }))
        .toBe(renamed);
    });
    expect(screen.getByTestId("names")).toHaveTextContent("Gym,Groceries,Pets");
    expect(categoryService.getCategories).toHaveBeenCalledTimes(2);
  });

  it("removes a deleted category even when the follow-up refresh fails", async () => {
    render(<CategoryProvider><Probe /></CategoryProvider>);
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("ready"));
    vi.mocked(categoryService.deleteCategory).mockResolvedValue(undefined);
    vi.mocked(categoryService.getCategories).mockRejectedValueOnce(new Error("network"));

    await act(async () => {
      await latest.deleteCategory(petCare.id);
    });

    expect(screen.getByTestId("names")).toHaveTextContent("Groceries");
    expect(screen.getByTestId("names")).not.toHaveTextContent("Pet Care");
    expect(screen.getByTestId("refresh-error")).not.toBeEmptyDOMElement();
  });

  it("does not change the list when a mutation fails", async () => {
    render(<CategoryProvider><Probe /></CategoryProvider>);
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("ready"));
    vi.mocked(categoryService.deleteCategory).mockRejectedValue(new Error("in use"));

    await act(async () => {
      await expect(latest.deleteCategory(petCare.id)).rejects.toThrow("in use");
    });

    expect(screen.getByTestId("names")).toHaveTextContent("Groceries,Pet Care");
    expect(categoryService.getCategories).toHaveBeenCalledTimes(1);
  });

  it("ignores a response that arrives after the account changed", async () => {
    const slow = deferred<CategoryResponse[]>();
    vi.mocked(categoryService.getCategories).mockReturnValueOnce(slow.promise);
    const { rerender } = render(<CategoryProvider><Probe /></CategoryProvider>);
    expect(categoryService.getCategories).toHaveBeenCalledTimes(1);

    signIn(otherUser);
    vi.mocked(categoryService.getCategories).mockResolvedValueOnce([theirs]);
    rerender(<CategoryProvider><Probe /></CategoryProvider>);
    await waitFor(() => expect(screen.getByTestId("names")).toHaveTextContent("Their Secret Category"));

    await act(async () => {
      slow.resolve([groceries, petCare]);
      await slow.promise;
    });

    expect(screen.getByTestId("names")).toHaveTextContent("Their Secret Category");
    expect(screen.getByTestId("names")).not.toHaveTextContent("Groceries");
  });

  it("never renders the previous user's categories, and clears them on sign-out", async () => {
    const { rerender } = render(<CategoryProvider><Probe /></CategoryProvider>);
    await waitFor(() => expect(screen.getByTestId("names")).toHaveTextContent("Groceries,Pet Care"));

    const next = deferred<CategoryResponse[]>();
    vi.mocked(categoryService.getCategories).mockReturnValueOnce(next.promise);
    signIn(otherUser);
    rerender(<CategoryProvider><Probe /></CategoryProvider>);

    // From the very first render for the new user, the old list is gone.
    expect(screen.getByTestId("names")).toBeEmptyDOMElement();
    await act(async () => {
      next.resolve([theirs]);
      await next.promise;
    });
    expect(screen.getByTestId("names")).toHaveTextContent("Their Secret Category");

    signIn(null);
    rerender(<CategoryProvider><Probe /></CategoryProvider>);
    expect(screen.getByTestId("names")).toBeEmptyDOMElement();
    await act(async () => {
      expect(await latest.reload()).toBeNull();
    });
    expect(screen.getByTestId("status")).toHaveTextContent("idle");
  });

  it("requires a provider", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    expect(() => render(<Probe />)).toThrow("useCategories must be used within a CategoryProvider");
  });
});
