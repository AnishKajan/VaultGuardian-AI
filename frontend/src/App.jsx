import React, { useState } from 'react';
import { AuthProvider, useAuth, LoginForm, RegisterForm } from './components/AuthComponents';
import VaultGuardianDashboard from './components/VaultGuardianDashboard';
import './index.css';

const AuthenticatedApp = () => {
  const { user, login, register, logout, loading } = useAuth();
  const [isLoginView, setIsLoginView] = useState(true);

  // Show loading spinner while checking authentication
  if (loading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-blue-900 via-blue-800 to-indigo-900 flex items-center justify-center">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-blue-400 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-white text-lg">Loading VaultGuardian AI...</p>
          <p className="text-blue-200 text-sm mt-2">Verifying your session...</p>
        </div>
      </div>
    );
  }

  // If user is authenticated, show dashboard
  if (user) {
    return <VaultGuardianDashboard user={user} onLogout={logout} />;
  }

  // If not authenticated, show login/register forms
  return isLoginView ? (
    <LoginForm 
      onLogin={login}
      onSwitchToRegister={() => setIsLoginView(false)}
    />
  ) : (
    <RegisterForm 
      onRegister={register}
      onSwitchToLogin={() => setIsLoginView(true)}
    />
  );
};

function App() {
  return (
    <AuthProvider>
      <div className="App">
        <AuthenticatedApp />
      </div>
    </AuthProvider>
  );
}

export default App;