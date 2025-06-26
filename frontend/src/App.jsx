import React, { useState } from 'react';
import { AuthProvider, useAuth, LoginForm, RegisterForm } from './components/AuthComponents';
import VaultGuardianDashboard from './components/VaultGuardianDashboard';

// Create a separate component that uses the auth context
function AppContent() {
  const { user, loading, login, register, logout } = useAuth();
  const [showLogin, setShowLogin] = useState(true);

  // Show loading spinner while checking auth
  if (loading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-blue-900 via-blue-800 to-indigo-900 flex items-center justify-center">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-blue-400 border-t-transparent rounded-full animate-spin mx-auto"></div>
          <p className="mt-4 text-blue-200 text-lg">Loading VaultGuardian AI...</p>
        </div>
      </div>
    );
  }

  // Show login/register if no user
  if (!user) {
    return showLogin ? (
      <LoginForm 
        onSwitchToRegister={() => setShowLogin(false)}
        onLogin={login}
      />
    ) : (
      <RegisterForm 
        onSwitchToLogin={() => setShowLogin(true)}
        onRegister={register}
      />
    );
  }

  // Show dashboard if user is authenticated
  return <VaultGuardianDashboard user={user} onLogout={logout} />;
}

// Main App component with AuthProvider
function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}

export default App;