import { Component, type ReactNode } from "react";
import { claimAutomaticReload, isPageLoadError } from "../utils/pageLoadRecovery";

interface PageErrorBoundaryProps {
  children: ReactNode;
  /** Defaults to a full page reload; tests pass a spy. */
  reload?: () => void;
}

interface PageErrorBoundaryState {
  failed: boolean;
  reloading: boolean;
}

/**
 * Catches errors from the routed page so the shell (sidebar, account menu) stays usable.
 * When a page's code failed to download, usually because a new version was deployed while
 * the tab was open, it reloads once to pick up the new files; otherwise, or if that already
 * happened within the last minute, it shows a message with a Reload button.
 *
 * AppLayout keys it by pathname, so moving to another page clears the error.
 */
export class PageErrorBoundary extends Component<PageErrorBoundaryProps, PageErrorBoundaryState> {
  state: PageErrorBoundaryState = { failed: false, reloading: false };

  static getDerivedStateFromError(): Partial<PageErrorBoundaryState> {
    return { failed: true };
  }

  componentDidCatch(error: unknown): void {
    // The reload decision has a side effect (it records the attempt), so it lives here and
    // not in getDerivedStateFromError, which React may call more than once.
    if (isPageLoadError(error) && claimAutomaticReload()) {
      this.setState({ reloading: true });
      this.reload();
    }
  }

  private reload = () => {
    (this.props.reload ?? (() => window.location.reload()))();
  };

  render() {
    if (this.state.reloading) {
      return (
        <section className="page-loading" aria-busy="true">
          <p className="empty-state" role="status">Loading the latest version…</p>
        </section>
      );
    }

    if (this.state.failed) {
      return (
        <section className="page-error" role="alert">
          <h1>This page couldn't load</h1>
          <p className="empty-state">
            Check your connection and reload. Your saved data is not affected.
          </p>
          <button type="button" className="button button--tinted" onClick={this.reload}>
            Reload
          </button>
        </section>
      );
    }

    return this.props.children;
  }
}
