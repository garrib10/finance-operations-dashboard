function AppHeader() {
  return (
    <header className="app-header">
      <div className="app-header__content">
        <a className="app-brand" href="/">
          FinTrack
        </a>

        <nav className="app-nav" aria-label="Primary navigation">
          <a href="/">Dashboard</a>
          <a href="/transactions">Transactions</a>
          <a href="/budgets">Budgets</a>
        </nav>
      </div>
    </header>
  );
}

export default AppHeader;
