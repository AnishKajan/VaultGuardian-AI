import React, { createContext, useContext, useState, useEffect } from 'react';
import { Shield, User, Lock, Eye, EyeOff, Mail, UserCheck } from 'lucide-react';
import { useSnackbar, SnackbarContainer } from './Snackbar';

// API Configuration
const API_BASE = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';
console.log('Auth API_BASE:', API_BASE, 'ENV:', process.env.REACT_APP_API_URL);

// API retry helper for Render wake-up
const apiCallWithRetry = async (url, options = {}, maxRetries = 3, retryDelay = 2000) => {
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      console.log(`Auth API call attempt ${attempt}/${maxRetries} to:`, url);
      
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 60000); // 60 second timeout
      
      const response = await fetch(url, {
        ...options,
        signal: controller.signal
      });
      
      clearTimeout(timeoutId);
      return response;
    } catch (error) {
      console.error(`Auth API call attempt ${attempt} failed:`, error.message);
      
      // If it's the last attempt, throw the error
      if (attempt === maxRetries) {
        throw error;
      }
      
      // Wait before retrying (longer wait for first retry to allow wake-up)
      const waitTime = attempt === 1 ? 10000 : retryDelay; // 10s first retry, then 2s
      console.log(`Waiting ${waitTime}ms before retry...`);
      await new Promise(resolve => setTimeout(resolve, waitTime));
    }
  }
};

// ========================================
// AUTH CONTEXT
// ========================================
const AuthContext = createContext();

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true); // Start as loading

  useEffect(() => {
    const storedToken = localStorage.getItem('token');
    console.log('AuthProvider init - stored token:', storedToken ? 'exists' : 'none');
    
    if (storedToken) {
      setToken(storedToken);
      validateToken(storedToken);
    } else {
      console.log('No token found, user needs to login');
      setLoading(false);
    }
  }, []); // Only run once on mount

  const validateToken = async (tokenToValidate = token) => {
    if (!tokenToValidate) {
      console.log('No token to validate');
      setLoading(false);
      return;
    }

    try {
      console.log('Validating token...');
      const response = await apiCallWithRetry(`${API_BASE}/auth/me`, {
        headers: {
          'Authorization': `Bearer ${tokenToValidate}`,
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        const userData = await response.json();
        console.log('Token validation successful, user:', userData.username);
        setUser(userData);
        setToken(tokenToValidate);
        localStorage.setItem('token', tokenToValidate);
      } else {
        console.log('Token validation failed, clearing auth');
        logout();
      }
    } catch (error) {
      console.error('Token validation error:', error);
      logout();
    } finally {
      setLoading(false);
    }
  };

  const parseErrorResponse = async (response) => {
    try {
      const errorData = await response.json();
      return {
        errorCode: errorData.errorCode,
        message: errorData.message || errorData.error,
        fullError: errorData
      };
    } catch {
      // Fallback to text if JSON parsing fails
      try {
        const errorText = await response.text();
        return {
          errorCode: 'UNKNOWN_ERROR',
          message: errorText || 'An unexpected error occurred',
          fullError: null
        };
      } catch {
        return {
          errorCode: 'NETWORK_ERROR',
          message: 'Network error. Please check your connection.',
          fullError: null
        };
      }
    }
  };

  const login = async (credentials) => {
    try {
      const response = await apiCallWithRetry(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(credentials),
      });

      if (!response.ok) {
        const errorInfo = await parseErrorResponse(response);
        return { 
          success: false, 
          error: errorInfo.message,
          errorCode: errorInfo.errorCode 
        };
      }

      const data = await response.json();
      console.log('Login successful, storing token and user data');
      
      // Store token and user data
      setToken(data.token);
      setUser({
        username: data.username,
        email: data.email,
        firstName: data.firstName,
        lastName: data.lastName,
        roles: data.roles
      });
      localStorage.setItem('token', data.token);
      
      return { success: true };
    } catch (error) {
      console.error('Login error:', error);
      return { 
        success: false, 
        error: 'Network error. Please check your connection.',
        errorCode: 'NETWORK_ERROR'
      };
    }
  };

  const register = async (userData) => {
    try {
      const response = await apiCallWithRetry(`${API_BASE}/auth/register`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(userData),
      });

      if (!response.ok) {
        const errorInfo = await parseErrorResponse(response);
        return { 
          success: false, 
          error: errorInfo.message,
          errorCode: errorInfo.errorCode 
        };
      }

      const data = await response.json();
      
      // Registration response doesn't include a token directly
      // Auto-login after successful registration
      console.log('Registration successful, data:', data);
      
      const loginResult = await login({
        username: userData.username,
        password: userData.password
      });
      
      if (loginResult.success) {
        return { success: true };
      } else {
        return {
          success: false,
          error: 'Account created but login failed. Please try logging in manually.',
          errorCode: 'AUTO_LOGIN_FAILED'
        };
      }
      
    } catch (error) {
      console.error('Registration error:', error);
      return { 
        success: false, 
        error: 'Network error. Please check your connection.',
        errorCode: 'NETWORK_ERROR'
      };
    }
  };

  const logout = () => {
    console.log('Logging out user');
    setUser(null);
    setToken(null);
    localStorage.removeItem('token');
    setLoading(false); // Make sure loading is false after logout
  };

  const value = {
    user,
    token,
    login,
    register,
    logout,
    loading
  };

  return React.createElement(AuthContext.Provider, { value }, children);
};

// ========================================
// LOGIN FORM COMPONENT
// ========================================
export const LoginForm = ({ onSwitchToRegister, onLogin }) => {
  const [credentials, setCredentials] = useState({
    username: '',
    password: ''
  });
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const { snackbars, showSuccess, showError, removeSnackbar } = useSnackbar();

  const getErrorMessage = (errorCode, defaultMessage) => {
    const errorMessages = {
      'ACCOUNT_NOT_FOUND': 'Account not found. Please check your username or create a new account.',
      'INCORRECT_PASSWORD': 'Incorrect password. Please try again.',
      'LOGIN_ERROR': 'Login failed. Please try again.',
      'NETWORK_ERROR': 'Network error. Please check your connection and try again.',
      'INVALID_CREDENTIALS': 'Invalid username or password.',
      'ACCOUNT_LOCKED': 'Account has been locked. Please contact support.',
      'ACCOUNT_DISABLED': 'Account has been disabled. Please contact support.'
    };
    
    return errorMessages[errorCode] || defaultMessage || 'Login failed. Please try again.';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);

    try {
      const result = await onLogin(credentials);
      
      if (result.success) {
        showSuccess('Login successful! Welcome to VaultGuardian AI.', 3000);
      } else {
        const userFriendlyMessage = getErrorMessage(result.errorCode, result.error);
        showError(userFriendlyMessage, 5000);
      }
    } catch (error) {
      showError('An unexpected error occurred. Please try again.', 5000);
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    setCredentials({
      ...credentials,
      [e.target.name]: e.target.value
    });
  };

  return React.createElement('div', {
    className: "min-h-screen bg-gradient-to-br from-blue-900 via-blue-800 to-indigo-900 flex items-center justify-center p-4"
  },
    React.createElement(SnackbarContainer, {
      snackbars,
      onRemove: removeSnackbar,
      position: 'bottom-left'
    }),
    React.createElement('div', {
      className: "max-w-md w-full space-y-8"
    },
      React.createElement('div', {
        className: "text-center"
      },
        React.createElement('div', {
          className: "flex justify-center"
        },
          React.createElement(Shield, {
            className: "h-16 w-16 text-blue-400"
          })
        ),
        React.createElement('h2', {
          className: "mt-6 text-3xl font-bold text-white"
        }, "VaultGuardian AI"),
        React.createElement('p', {
          className: "mt-2 text-sm text-blue-200"
        }, "Sign in to your secure document vault")
      ),
      React.createElement('form', {
        className: "mt-8 space-y-6",
        onSubmit: handleSubmit
      },
        React.createElement('div', {
          className: "bg-white/10 backdrop-blur-md rounded-2xl p-8 border border-white/20"
        },
          React.createElement('div', {
            className: "space-y-4"
          },
            React.createElement('div', null,
              React.createElement('label', {
                htmlFor: "username",
                className: "block text-sm font-medium text-blue-100 mb-2"
              }, "Username"),
              React.createElement('div', {
                className: "relative"
              },
                React.createElement(User, {
                  className: "absolute left-3 top-3 h-5 w-5 text-blue-300"
                }),
                React.createElement('input', {
                  id: "username",
                  name: "username",
                  type: "text",
                  required: true,
                  value: credentials.username,
                  onChange: handleChange,
                  className: "w-full pl-10 pr-4 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder-blue-200 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-transparent",
                  placeholder: "Enter your username"
                })
              )
            ),
            React.createElement('div', null,
              React.createElement('label', {
                htmlFor: "password",
                className: "block text-sm font-medium text-blue-100 mb-2"
              }, "Password"),
              React.createElement('div', {
                className: "relative"
              },
                React.createElement(Lock, {
                  className: "absolute left-3 top-3 h-5 w-5 text-blue-300"
                }),
                React.createElement('input', {
                  id: "password",
                  name: "password",
                  type: showPassword ? "text" : "password",
                  required: true,
                  value: credentials.password,
                  onChange: handleChange,
                  className: "w-full pl-10 pr-12 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder-blue-200 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-transparent",
                  placeholder: "Enter your password"
                }),
                React.createElement('button', {
                  type: "button",
                  onClick: () => setShowPassword(!showPassword),
                  className: "absolute right-3 top-3 h-5 w-5 text-blue-300 hover:text-white focus:outline-none"
                }, showPassword ? React.createElement(EyeOff) : React.createElement(Eye))
              )
            )
          ),
          React.createElement('button', {
            type: "submit",
            disabled: loading,
            className: "w-full mt-6 py-3 px-4 bg-gradient-to-r from-blue-500 to-indigo-600 hover:from-blue-600 hover:to-indigo-700 text-white font-semibold rounded-lg shadow-lg transform transition-all duration-200 hover:scale-105 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:ring-offset-2 focus:ring-offset-transparent disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none"
          },
            loading ?
              React.createElement('div', {
                className: "flex items-center justify-center"
              },
                React.createElement('div', {
                  className: "w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin mr-2"
                }),
                "Signing in..."
              ) : "Sign In"
          ),
          React.createElement('div', {
            className: "mt-6 text-center"
          },
            React.createElement('p', {
              className: "text-blue-200"
            },
              "Don't have an account? ",
              React.createElement('button', {
                type: "button",
                onClick: onSwitchToRegister,
                className: "text-blue-400 hover:text-white font-semibold focus:outline-none"
              }, "Sign up")
            )
          )
        )
      ),
      React.createElement('div', {
        className: "text-center"
      },
        React.createElement('p', {
          className: "text-xs text-blue-300"
        }, "Secured with enterprise-grade encryption")
      )
    )
  );
};

// ========================================
// REGISTER FORM COMPONENT
// ========================================
export const RegisterForm = ({ onSwitchToLogin, onRegister }) => {
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    firstName: '',
    lastName: ''
  });
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const { snackbars, showSuccess, showError, removeSnackbar } = useSnackbar();

  const getErrorMessage = (errorCode, defaultMessage) => {
    const errorMessages = {
      'USERNAME_EXISTS': 'Username already taken. Please choose a different username.',
      'EMAIL_EXISTS': 'Email already registered. Please use a different email or sign in.',
      'REGISTRATION_ERROR': 'Registration failed. Please try again.',
      'NETWORK_ERROR': 'Network error. Please check your connection and try again.',
      'INVALID_EMAIL': 'Please enter a valid email address.',
      'WEAK_PASSWORD': 'Password is too weak. Please use a stronger password.',
      'INVALID_USERNAME': 'Username contains invalid characters. Please use only letters, numbers, and underscores.'
    };
    
    return errorMessages[errorCode] || defaultMessage || 'Registration failed. Please try again.';
  };

  const validateForm = () => {
    // Check for incomplete required fields
    if (!formData.username.trim() || !formData.email.trim() || !formData.password.trim() || !formData.confirmPassword.trim()) {
      showError('Please fill in all required fields (Username, Email, Password, Confirm Password).', 5000);
      return false;
    }

    if (formData.password !== formData.confirmPassword) {
      showError('Passwords do not match. Please check your password confirmation.', 5000);
      return false;
    }

    if (formData.password.length < 6) {
      showError('Password must be at least 6 characters long.', 5000);
      return false;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(formData.email)) {
      showError('Please enter a valid email address.', 5000);
      return false;
    }

    return true;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setLoading(true);

    try {
      const result = await onRegister({
        username: formData.username.trim(),
        email: formData.email.trim(),
        password: formData.password,
        firstName: formData.firstName.trim(),
        lastName: formData.lastName.trim()
      });

      if (result.success) {
        showSuccess('Account created successfully! Welcome to VaultGuardian AI.', 3000);
      } else {
        const userFriendlyMessage = getErrorMessage(result.errorCode, result.error);
        showError(userFriendlyMessage, 5000);
      }
    } catch (error) {
      showError('An unexpected error occurred. Please try again.', 5000);
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  return React.createElement('div', {
    className: "min-h-screen bg-gradient-to-br from-blue-900 via-blue-800 to-indigo-900 flex items-center justify-center p-4"
  },
    React.createElement(SnackbarContainer, {
      snackbars,
      onRemove: removeSnackbar,
      position: 'bottom-left'
    }),
    React.createElement('div', {
      className: "max-w-md w-full space-y-8"
    },
      React.createElement('div', {
        className: "text-center"
      },
        React.createElement('div', {
          className: "flex justify-center"
        },
          React.createElement(Shield, {
            className: "h-16 w-16 text-blue-400"
          })
        ),
        React.createElement('h2', {
          className: "mt-6 text-3xl font-bold text-white"
        }, "Join VaultGuardian AI"),
        React.createElement('p', {
          className: "mt-2 text-sm text-blue-200"
        }, "Create your secure document vault account")
      ),
      React.createElement('form', {
        className: "mt-8 space-y-6",
        onSubmit: handleSubmit
      },
        React.createElement('div', {
          className: "bg-white/10 backdrop-blur-md rounded-2xl p-8 border border-white/20"
        },
          React.createElement('div', {
            className: "space-y-4"
          },
            React.createElement('div', {
              className: "grid grid-cols-2 gap-4"
            },
              React.createElement('div', null,
                React.createElement('label', {
                  className: "block text-sm font-medium text-blue-100 mb-2"
                }, "First Name"),
                React.createElement('input', {
                  name: "firstName",
                  type: "text",
                  value: formData.firstName,
                  onChange: handleChange,
                  className: "w-full px-4 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder-blue-200 focus:outline-none focus:ring-2 focus:ring-blue-400",
                  placeholder: "John"
                })
              ),
              React.createElement('div', null,
                React.createElement('label', {
                  className: "block text-sm font-medium text-blue-100 mb-2"
                }, "Last Name"),
                React.createElement('input', {
                  name: "lastName",
                  type: "text",
                  value: formData.lastName,
                  onChange: handleChange,
                  className: "w-full px-4 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder-blue-200 focus:outline-none focus:ring-2 focus:ring-blue-400",
                  placeholder: "Doe"
                })
              )
            ),
            React.createElement('div', null,
              React.createElement('label', {
                className: "block text-sm font-medium text-blue-100 mb-2"
              }, "Username *"),
              React.createElement('input', {
                name: "username",
                type: "text",
                required: true,
                value: formData.username,
                onChange: handleChange,
                className: "w-full px-4 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder-blue-200 focus:outline-none focus:ring-2 focus:ring-blue-400",
                placeholder: "johndoe"
              })
            ),
            React.createElement('div', null,
              React.createElement('label', {
                className: "block text-sm font-medium text-blue-100 mb-2"
              }, "Email *"),
              React.createElement('input', {
                name: "email",
                type: "email",
                required: true,
                value: formData.email,
                onChange: handleChange,
                className: "w-full px-4 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder-blue-200 focus:outline-none focus:ring-2 focus:ring-blue-400",
                placeholder: "john@example.com"
              })
            ),
            React.createElement('div', null,
              React.createElement('label', {
                className: "block text-sm font-medium text-blue-100 mb-2"
              }, "Password *"),
              React.createElement('div', {
                className: "relative"
              },
                React.createElement('input', {
                  name: "password",
                  type: showPassword ? "text" : "password",
                  required: true,
                  value: formData.password,
                  onChange: handleChange,
                  className: "w-full px-4 py-3 pr-12 bg-white/10 border border-white/20 rounded-lg text-white placeholder-blue-200 focus:outline-none focus:ring-2 focus:ring-blue-400",
                  placeholder: "Enter password (6+ characters)"
                }),
                React.createElement('button', {
                  type: "button",
                  onClick: () => setShowPassword(!showPassword),
                  className: "absolute right-3 top-3 h-5 w-5 text-blue-300 hover:text-white"
                }, showPassword ? React.createElement(EyeOff) : React.createElement(Eye))
              )
            ),
            React.createElement('div', null,
              React.createElement('label', {
                className: "block text-sm font-medium text-blue-100 mb-2"
              }, "Confirm Password *"),
              React.createElement('input', {
                name: "confirmPassword",
                type: "password",
                required: true,
                value: formData.confirmPassword,
                onChange: handleChange,
                className: "w-full px-4 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder-blue-200 focus:outline-none focus:ring-2 focus:ring-blue-400",
                placeholder: "Confirm password"
              })
            )
          ),
          React.createElement('button', {
            type: "submit",
            disabled: loading,
            className: "w-full mt-6 py-3 px-4 bg-gradient-to-r from-blue-500 to-indigo-600 hover:from-blue-600 hover:to-indigo-700 text-white font-semibold rounded-lg shadow-lg transform transition-all duration-200 hover:scale-105 focus:outline-none focus:ring-2 focus:ring-blue-400 disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none"
          },
            loading ?
              React.createElement('div', {
                className: "flex items-center justify-center"
              },
                React.createElement('div', {
                  className: "w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin mr-2"
                }),
                "Creating account..."
              ) : "Create Account"
          ),
          React.createElement('div', {
            className: "mt-6 text-center"
          },
            React.createElement('p', {
              className: "text-blue-200"
            },
              "Already have an account? ",
              React.createElement('button', {
                type: "button",
                onClick: onSwitchToLogin,
                className: "text-blue-400 hover:text-white font-semibold focus:outline-none"
              }, "Sign in")
            )
          )
        )
      )
    )
  );
};