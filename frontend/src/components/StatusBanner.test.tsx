import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { STATUS_BANNER_DURATION_MS, StatusBanner } from "./StatusBanner";

describe("StatusBanner", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("keeps an empty live region mounted so later messages are announced", () => {
    render(<StatusBanner message="" onDismiss={vi.fn()} />);

    expect(screen.getByRole("status")).toBeEmptyDOMElement();
    expect(screen.queryByRole("button", { name: "Dismiss message" })).not.toBeInTheDocument();
  });

  it("renders in the shared stack at the top of the window", () => {
    render(<><StatusBanner message="First." onDismiss={vi.fn()} /><StatusBanner message="Second." onDismiss={vi.fn()} /></>);

    const stack = document.getElementById("status-banner-stack");
    expect(stack).toContainElement(screen.getByText("First."));
    expect(stack).toContainElement(screen.getByText("Second."));
  });

  it("dismisses itself after the default duration", () => {
    const onDismiss = vi.fn();
    render(<StatusBanner message="Budget created." onDismiss={onDismiss} />);

    expect(screen.getByRole("status")).toHaveTextContent("Budget created.");
    act(() => vi.advanceTimersByTime(STATUS_BANNER_DURATION_MS - 1));
    expect(onDismiss).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(1));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it("does not restart the timer when the parent re-renders with a new callback", () => {
    const onDismiss = vi.fn();
    const { rerender } = render(<StatusBanner message="Saved." onDismiss={() => onDismiss()} />);

    act(() => vi.advanceTimersByTime(STATUS_BANNER_DURATION_MS - 100));
    rerender(<StatusBanner message="Saved." onDismiss={() => onDismiss()} />);
    act(() => vi.advanceTimersByTime(100));

    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it("pauses while hovered and resumes when the pointer leaves", () => {
    const onDismiss = vi.fn();
    render(<StatusBanner message="Saved." onDismiss={onDismiss} durationMs={1000} />);
    const banner = screen.getByText("Saved.").parentElement!;

    fireEvent.mouseEnter(banner);
    act(() => vi.advanceTimersByTime(5000));
    expect(onDismiss).not.toHaveBeenCalled();

    fireEvent.mouseLeave(banner);
    act(() => vi.advanceTimersByTime(1000));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it("pauses while keyboard focus is inside and closes from the dismiss button", () => {
    const onDismiss = vi.fn();
    render(<StatusBanner message="Saved." onDismiss={onDismiss} durationMs={1000} />);
    const dismiss = screen.getByRole("button", { name: "Dismiss message" });

    act(() => dismiss.focus());
    act(() => vi.advanceTimersByTime(5000));
    expect(onDismiss).not.toHaveBeenCalled();

    fireEvent.click(dismiss);
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it("resumes after focus leaves", () => {
    const onDismiss = vi.fn();
    render(<StatusBanner message="Saved." onDismiss={onDismiss} durationMs={1000} />);
    const dismiss = screen.getByRole("button", { name: "Dismiss message" });

    act(() => dismiss.focus());
    act(() => dismiss.blur());
    act(() => vi.advanceTimersByTime(1000));

    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});
