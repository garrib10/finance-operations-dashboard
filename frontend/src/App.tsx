import "./App.css";

import { Navigate, Route, Routes } from "react-router-dom";

import ProfilePage from "./pages/ProfilePage";
import AccountSettingsPage from "./pages/AccountSettingsPage";

import { AppLayout } from "./components/AppLayout";
import { PublicLayout } from "./components/PublicLayout";
import { CategoryProvider } from "./context/CategoryProvider";
import ProtectedRoute from "./components/ProtectedRoute";
import BudgetPage from "./pages/BudgetPage";
import DashboardPage from "./pages/DashboardPage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import TransactionPage from "./pages/TransactionPage";

function App() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <CategoryProvider>
        <Routes>
          <Route element={<PublicLayout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>

          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/settings" element={<AccountSettingsPage />} />
              <Route path="/" element={<DashboardPage />} />
              <Route path="/transactions" element={<TransactionPage />} />
              <Route path="/budgets" element={<BudgetPage />} />
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </CategoryProvider>
    </div>
  );
}

export default App;
