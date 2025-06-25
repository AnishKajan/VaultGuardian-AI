// components/Snackbar.js
import React, { useState, useEffect } from 'react';
import { CheckCircle, XCircle, AlertCircle, X } from 'lucide-react';

export const Snackbar = ({ 
  message, 
  type = 'info', 
  isVisible, 
  onClose, 
  duration = 5000,
  position = 'top-right' 
}) => {
  const [isAnimating, setIsAnimating] = useState(false);

  useEffect(() => {
    if (isVisible) {
      setIsAnimating(true);
      const timer = setTimeout(() => {
        handleClose();
      }, duration);

      return () => clearTimeout(timer);
    }
  }, [isVisible, duration]);

  const handleClose = () => {
    setIsAnimating(false);
    setTimeout(() => {
      onClose();
    }, 300); // Animation duration
  };

  const getSnackbarStyles = () => {
    const baseStyles = "fixed z-50 flex items-center p-4 max-w-md rounded-lg shadow-lg transition-all duration-300 transform";
    
    const positionStyles = {
      'top-right': 'top-4 right-4',
      'top-left': 'top-4 left-4',
      'top-center': 'top-4 left-1/2 transform -translate-x-1/2',
      'bottom-right': 'bottom-4 right-4',
      'bottom-left': 'bottom-4 left-4',
      'bottom-center': 'bottom-4 left-1/2 transform -translate-x-1/2'
    };

    const typeStyles = {
      success: 'bg-green-50 border border-green-200 text-green-800',
      error: 'bg-red-50 border border-red-200 text-red-800',
      warning: 'bg-yellow-50 border border-yellow-200 text-yellow-800',
      info: 'bg-blue-50 border border-blue-200 text-blue-800'
    };

    const animationStyles = isAnimating 
      ? 'translate-y-0 opacity-100 scale-100' 
      : 'translate-y-2 opacity-0 scale-95';

    return `${baseStyles} ${positionStyles[position]} ${typeStyles[type]} ${animationStyles}`;
  };

  const getIcon = () => {
    const iconProps = { className: "w-5 h-5 mr-3 flex-shrink-0" };
    
    switch (type) {
      case 'success':
        return <CheckCircle {...iconProps} className="w-5 h-5 mr-3 flex-shrink-0 text-green-600" />;
      case 'error':
        return <XCircle {...iconProps} className="w-5 h-5 mr-3 flex-shrink-0 text-red-600" />;
      case 'warning':
        return <AlertCircle {...iconProps} className="w-5 h-5 mr-3 flex-shrink-0 text-yellow-600" />;
      default:
        return <AlertCircle {...iconProps} className="w-5 h-5 mr-3 flex-shrink-0 text-blue-600" />;
    }
  };

  if (!isVisible) return null;

  return (
    <div className={getSnackbarStyles()}>
      {getIcon()}
      <div className="flex-1 text-sm font-medium">
        {message}
      </div>
      <button
        onClick={handleClose}
        className="ml-3 p-1 rounded-full hover:bg-white/50 transition-colors"
        aria-label="Close notification"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
};

// Snackbar Hook for easy usage
export const useSnackbar = () => {
  const [snackbars, setSnackbars] = useState([]);

  const showSnackbar = (message, type = 'info', duration = 5000) => {
    const id = Date.now() + Math.random();
    const newSnackbar = {
      id,
      message,
      type,
      duration,
      isVisible: true
    };

    setSnackbars(prev => [...prev, newSnackbar]);

    // Auto remove after duration
    setTimeout(() => {
      removeSnackbar(id);
    }, duration);

    return id;
  };

  const removeSnackbar = (id) => {
    setSnackbars(prev => prev.filter(snackbar => snackbar.id !== id));
  };

  const showSuccess = (message, duration) => showSnackbar(message, 'success', duration);
  const showError = (message, duration) => showSnackbar(message, 'error', duration);
  const showWarning = (message, duration) => showSnackbar(message, 'warning', duration);
  const showInfo = (message, duration) => showSnackbar(message, 'info', duration);

  return {
    snackbars,
    showSnackbar,
    showSuccess,
    showError,
    showWarning,
    showInfo,
    removeSnackbar
  };
};

// Snackbar Container Component
export const SnackbarContainer = ({ snackbars, onRemove, position = 'top-right' }) => {
  return (
    <div className="fixed z-50 pointer-events-none">
      {snackbars.map((snackbar, index) => (
        <div
          key={snackbar.id}
          className="pointer-events-auto"
          style={{
            [position.includes('top') ? 'top' : 'bottom']: `${(index * 80) + 16}px`,
            [position.includes('right') ? 'right' : 'left']: '16px',
            ...(position.includes('center') && {
              left: '50%',
              transform: 'translateX(-50%)'
            })
          }}
        >
          <Snackbar
            message={snackbar.message}
            type={snackbar.type}
            isVisible={snackbar.isVisible}
            onClose={() => onRemove(snackbar.id)}
            duration={snackbar.duration}
            position={position}
          />
        </div>
      ))}
    </div>
  );
};