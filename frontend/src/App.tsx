import "./App.css";
import AppHeader from "./components/AppHeader";
import DashboardPage from "./pages/DashboardPage";

function App() {
  return (
    <div className="app-shell">
      <AppHeader />

      <main className="app-main">
        <DashboardPage />
      </main>
    </div>
  );
}

export default App;
