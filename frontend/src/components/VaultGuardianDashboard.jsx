import React, { useState, useEffect } from 'react';
import { Upload, Shield, AlertTriangle, FileText, Eye, Download, Trash2, LogOut, User, XCircle, AlertCircle } from 'lucide-react';
import { useAuth } from './AuthComponents';
import { useSnackbar, SnackbarContainer } from './Snackbar';

// API Configuration with retry logic for Render
const API_BASE = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';
console.log('API_BASE:', API_BASE, 'ENV:', process.env.REACT_APP_API_URL);

// API retry helper for Render wake-up
const apiCallWithRetry = async (url, options = {}, maxRetries = 3, retryDelay = 2000) => {
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      console.log(`API call attempt ${attempt}/${maxRetries} to:`, url);
      
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 60000); // 60 second timeout
      
      const response = await fetch(url, {
        ...options,
        signal: controller.signal
      });
      
      clearTimeout(timeoutId);
      return response;
    } catch (error) {
      console.error(`API call attempt ${attempt} failed:`, error.message);
      
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

// Service Wake-Up Loading Component
const ServiceWakeUpMessage = ({ isVisible }) => {
  if (!isVisible) return null;
  
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 max-w-md mx-4 text-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
        <h3 className="text-lg font-semibold text-gray-900 mb-2">
          Starting Secure Service
        </h3>
        <p className="text-gray-600 text-sm">
          Your backend service is waking up from sleep mode. This usually takes 30-60 seconds on the first request.
        </p>
        <p className="text-gray-500 text-xs mt-2">
          This delay only happens on the free tier.
        </p>
      </div>
    </div>
  );
};

// Risk Level Guide Component
const RiskLevelGuide = ({ className = "" }) => {
  const riskLevels = [
    {
      level: 'LOW',
      color: 'bg-green-500',
      textColor: 'text-green-800',
      bgColor: 'bg-green-50',
      icon: <Shield className="h-4 w-4" />,
      description: 'No security issues detected',
      examples: 'Clean documents with no sensitive data'
    },
    {
      level: 'MEDIUM',
      color: 'bg-yellow-500',
      textColor: 'text-yellow-800',
      bgColor: 'bg-yellow-50',
      icon: <AlertTriangle className="h-4 w-4" />,
      description: 'Minor security concerns detected',
      examples: 'Email addresses, phone numbers, or policy violations'
    },
    {
      level: 'HIGH',
      color: 'bg-orange-500',
      textColor: 'text-orange-800',
      bgColor: 'bg-orange-50',
      icon: <AlertCircle className="h-4 w-4" />,
      description: 'Significant security risks found',
      examples: 'SSN, credit cards, passwords, or API keys'
    },
    {
      level: 'CRITICAL',
      color: 'bg-red-500',
      textColor: 'text-red-800',
      bgColor: 'bg-red-50',
      icon: <XCircle className="h-4 w-4" />,
      description: 'Severe security threats detected',
      examples: 'Malware, multiple PII items, or classified content'
    }
  ];

  return (
    <div className={`bg-white rounded-lg shadow p-6 ${className}`}>
      <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center">
        <Shield className="h-5 w-5 text-blue-600 mr-2" />
        Risk Level Guide
      </h3>
      <div className="space-y-3">
        {riskLevels.map((risk) => (
          <div key={risk.level} className={`p-3 rounded-lg ${risk.bgColor} border border-opacity-20`}>
            <div className="flex items-start space-x-3">
              <div className={`flex items-center justify-center w-6 h-6 rounded-full ${risk.color} text-white flex-shrink-0 mt-0.5`}>
                {risk.icon}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center space-x-2 mb-1">
                  <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${risk.color.replace('bg-', 'bg-opacity-20 text-')} border`}>
                    {risk.level}
                  </span>
                  <span className={`text-sm font-medium ${risk.textColor}`}>
                    {risk.description}
                  </span>
                </div>
                <p className="text-xs text-gray-600 leading-relaxed">
                  {risk.examples}
                </p>
              </div>
            </div>
          </div>
        ))}
      </div>
      
      {/* Processing Flow Info */}
      <div className="mt-4 pt-4 border-t border-gray-200">
        <h4 className="text-sm font-semibold text-gray-700 mb-2">Document Processing Flow:</h4>
        <div className="text-xs text-gray-600 space-y-1">
          <div className="flex items-center space-x-2">
            <div className="w-2 h-2 bg-blue-500 rounded-full"></div>
            <span><strong>Upload:</strong> File uploaded to secure S3 storage</span>
          </div>
          <div className="flex items-center space-x-2">
            <div className="w-2 h-2 bg-purple-500 rounded-full"></div>
            <span><strong>Scanning:</strong> Malware and PII detection</span>
          </div>
          <div className="flex items-center space-x-2">
            <div className="w-2 h-2 bg-yellow-500 rounded-full"></div>
            <span><strong>Analyzing:</strong> AI content analysis with Llama3</span>
          </div>
          <div className="flex items-center space-x-2">
            <div className="w-2 h-2 bg-orange-500 rounded-full"></div>
            <span><strong>Policy Check:</strong> Security policy enforcement</span>
          </div>
          <div className="flex items-center space-x-2">
            <div className="w-2 h-2 bg-green-500 rounded-full"></div>
            <span><strong>Final Decision:</strong> Approved, Quarantined, or Rejected</span>
          </div>
        </div>
      </div>
    </div>
  );
};

// Security Alert Modal Component
const SecurityAlertModal = ({ isOpen, onClose, document }) => {
  if (!isOpen || !document) return null;

  const getSecurityAlerts = (detectedFlags) => {
    const alerts = [];
    
    // Count specific security issues
    const ssnCount = detectedFlags.filter(flag => 
      flag.includes('Social Security Number') || flag.includes('SSN')
    ).length;
    
    const creditCardCount = detectedFlags.filter(flag => 
      flag.includes('Credit Card')
    ).length;
    
    const emailCount = detectedFlags.filter(flag => 
      flag.includes('Email')
    ).length;
    
    const phoneCount = detectedFlags.filter(flag => 
      flag.includes('Phone')
    ).length;

    if (ssnCount > 0) {
      alerts.push({
        type: 'critical',
        icon: <XCircle className="h-5 w-5" />,
        message: `${ssnCount} Social Security Number${ssnCount > 1 ? 's' : ''} exposed`,
        description: 'SSNs are highly sensitive and should be encrypted or removed.'
      });
    }

    if (creditCardCount > 0) {
      alerts.push({
        type: 'critical',
        icon: <XCircle className="h-5 w-5" />,
        message: `${creditCardCount} Credit Card${creditCardCount > 1 ? 's' : ''} exposed`,
        description: 'Credit card numbers pose financial security risks.'
      });
    }

    if (emailCount > 0) {
      alerts.push({
        type: 'warning',
        icon: <AlertTriangle className="h-5 w-5" />,
        message: `${emailCount} Email Address${emailCount > 1 ? 'es' : ''} detected`,
        description: 'Email addresses may violate privacy policies.'
      });
    }

    if (phoneCount > 0) {
      alerts.push({
        type: 'warning',
        icon: <AlertTriangle className="h-5 w-5" />,
        message: `${phoneCount} Phone Number${phoneCount > 1 ? 's' : ''} detected`,
        description: 'Phone numbers are considered personal information.'
      });
    }

    return alerts;
  };

  const securityAlerts = getSecurityAlerts(document.detectedFlags || []);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-96 overflow-y-auto">
        <div className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-semibold text-gray-900 flex items-center">
              <AlertTriangle className="h-5 w-5 text-red-600 mr-2" />
              Security Alert: {document.originalFilename}
            </h3>
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-600 transition-colors"
            >
              <XCircle className="h-6 w-6" />
            </button>
          </div>

          <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg">
            <div className="flex items-center space-x-2 mb-2">
              <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                document.riskLevel === 'CRITICAL' ? 'bg-red-200 text-red-800' :
                document.riskLevel === 'HIGH' ? 'bg-orange-200 text-orange-800' :
                document.riskLevel === 'MEDIUM' ? 'bg-yellow-200 text-yellow-800' :
                'bg-green-200 text-green-800'
              }`}>
                {document.riskLevel} RISK
              </span>
              <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                document.status === 'REJECTED' ? 'bg-red-200 text-red-800' :
                document.status === 'QUARANTINED' ? 'bg-orange-200 text-orange-800' :
                'bg-green-200 text-green-800'
              }`}>
                {document.status}
              </span>
            </div>
            <p className="text-sm text-red-800">
              This document has been flagged for containing sensitive information that may violate security policies.
            </p>
          </div>

          {securityAlerts.length > 0 && (
            <div className="space-y-3">
              <h4 className="font-medium text-gray-900">Detected Security Issues:</h4>
              {securityAlerts.map((alert, index) => (
                <div key={index} className={`p-3 rounded-lg border ${
                  alert.type === 'critical' ? 'bg-red-50 border-red-200' : 'bg-yellow-50 border-yellow-200'
                }`}>
                  <div className="flex items-start space-x-3">
                    <div className={`flex-shrink-0 ${
                      alert.type === 'critical' ? 'text-red-600' : 'text-yellow-600'
                    }`}>
                      {alert.icon}
                    </div>
                    <div>
                      <p className={`font-medium text-sm ${
                        alert.type === 'critical' ? 'text-red-800' : 'text-yellow-800'
                      }`}>
                        {alert.message}
                      </p>
                      <p className={`text-xs mt-1 ${
                        alert.type === 'critical' ? 'text-red-700' : 'text-yellow-700'
                      }`}>
                        {alert.description}
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="mt-6 pt-4 border-t border-gray-200">
            <h4 className="font-medium text-gray-900 mb-2">Recommended Actions:</h4>
            <ul className="text-sm text-gray-600 space-y-1 list-disc list-inside">
              <li>Remove or redact all sensitive information</li>
              <li>Use encryption for documents containing PII</li>
              <li>Contact security team for policy guidance</li>
              <li>Consider using secure document sharing alternatives</li>
            </ul>
          </div>

          <div className="mt-6 flex justify-end space-x-3">
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

const VaultGuardianDashboard = ({ user, onLogout }) => {
  const { token } = useAuth();
  const [documents, setDocuments] = useState([]);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [isUploading, setIsUploading] = useState(false);
  const [isServiceWaking, setIsServiceWaking] = useState(false);
  const [analytics, setAnalytics] = useState({
    totalDocuments: 0,
    quarantinedDocuments: 0,
    highRiskDocuments: 0,
    riskDistribution: []
  });
  const [selectedDocument, setSelectedDocument] = useState(null);
  const [showSecurityAlert, setShowSecurityAlert] = useState(false);
  
  const { snackbars, showSuccess, showError, showWarning, removeSnackbar } = useSnackbar();

  // API helper function with retry logic
  const apiCall = async (endpoint, options = {}) => {
    const url = `${API_BASE}${endpoint}`;
    const config = {
      headers: {
        'Content-Type': 'application/json',
        ...(token && { 'Authorization': `Bearer ${token}` }),
        ...options.headers,
      },
      ...options,
    };

    // Show wake-up message for first requests
    if (!documents.length && endpoint.includes('/documents')) {
      setIsServiceWaking(true);
    }

    try {
      const response = await apiCallWithRetry(url, config);
      setIsServiceWaking(false);
      
      if (!response.ok) {
        throw new Error(`API call failed: ${response.statusText}`);
      }
      
      return response;
    } catch (error) {
      setIsServiceWaking(false);
      throw error;
    }
  };

  // Parse error response for uploads
  const parseUploadError = async (response) => {
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
          errorCode: 'UPLOAD_ERROR',
          message: errorText || 'Upload failed',
          fullError: null
        };
      } catch {
        return {
          errorCode: 'UPLOAD_ERROR',
          message: 'Upload failed',
          fullError: null
        };
      }
    }
  };

  const getUploadErrorMessage = (errorCode, defaultMessage) => {
    const errorMessages = {
      'FILE_TOO_LARGE': 'File size exceeds the maximum limit (50MB). Please choose a smaller file.',
      'INVALID_FILE_TYPE': 'File type not supported. Please upload PDF, DOCX, XLSX, or TXT files only.',
      'MALWARE_DETECTED': 'Security threat detected in file. Upload blocked for your protection.',
      'QUOTA_EXCEEDED': 'Storage quota exceeded. Please delete some files or upgrade your plan.',
      'UPLOAD_ERROR': 'Upload failed. Please try again.',
      'NETWORK_ERROR': 'Network error. Please check your connection and try again.',
      'PROCESSING_ERROR': 'File processing failed. Please try uploading again.',
      'UNAUTHORIZED': 'Session expired. Please sign in again.'
    };
    
    return errorMessages[errorCode] || defaultMessage || 'Upload failed. Please try again.';
  };

  // Load documents and analytics
  useEffect(() => {
    loadDocuments();
    loadAnalytics();
  }, [token]);

  // Auto-refresh effect for processing documents
  useEffect(() => {
    const hasProcessingDocs = documents.some(doc => 
      doc.status === 'SCANNING' || doc.status === 'ANALYZING'
    );

    if (hasProcessingDocs) {
      const interval = setInterval(() => {
        loadDocuments(); // Refresh document list
        loadAnalytics(); // Refresh dashboard stats
      }, 3000); // Check every 3 seconds

      return () => clearInterval(interval);
    }
  }, [documents]);

  const loadDocuments = async () => {
    try {
      const response = await apiCall('/documents');
      const docs = await response.json();
      setDocuments(docs);
    } catch (error) {
      console.error('Failed to load documents:', error);
      showError('Failed to load documents. Please refresh the page.', 5000);
      // Use mock data as fallback
      setDocuments([
        {
          id: 1,
          originalFilename: "financial_report_2024.pdf",
          status: "APPROVED",
          riskLevel: "LOW",
          detectedFlags: [],
          isQuarantined: false,
          createdAt: "2024-06-20T10:30:00",
          fileSize: 2048576
        }
      ]);
    }
  };

  const loadAnalytics = async () => {
    try {
      const response = await apiCall('/documents/analytics/dashboard');
      const data = await response.json();
      setAnalytics(data);
    } catch (error) {
      console.error('Failed to load analytics:', error);
      // Use mock data as fallback
      setAnalytics({
        totalDocuments: documents.length,
        quarantinedDocuments: documents.filter(d => d.isQuarantined).length,
        highRiskDocuments: documents.filter(d => d.riskLevel === 'HIGH' || d.riskLevel === 'CRITICAL').length,
        riskDistribution: []
      });
    }
  };

  const handleFileUpload = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    // Client-side validation
    const maxSize = 50 * 1024 * 1024; // 50MB
    const allowedTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 
                         'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'text/plain'];

    if (file.size > maxSize) {
      showError('File size exceeds 50MB limit. Please choose a smaller file.', 5000);
      event.target.value = '';
      return;
    }

    if (!allowedTypes.includes(file.type)) {
      showError('File type not supported. Please upload PDF, DOCX, XLSX, or TXT files only.', 5000);
      event.target.value = '';
      return;
    }

    setIsUploading(true);
    setUploadProgress(0);

    try {
      const formData = new FormData();
      formData.append('file', file);

      // Simulate progress updates
      const progressInterval = setInterval(() => {
        setUploadProgress(prev => {
          if (prev >= 90) {
            clearInterval(progressInterval);
            return 90; // Keep at 90% until upload completes
          }
          return prev + 10;
        });
      }, 200);

      const response = await apiCallWithRetry(`${API_BASE}/documents/upload`, {
        method: 'POST',
        headers: {
          ...(token && { 'Authorization': `Bearer ${token}` }),
        },
        body: formData,
      });

      clearInterval(progressInterval);
      setUploadProgress(100);

      if (!response.ok) {
        const errorInfo = await parseUploadError(response);
        const userFriendlyMessage = getUploadErrorMessage(errorInfo.errorCode, errorInfo.message);
        showError(userFriendlyMessage, 6000);
        return;
      }

      const uploadedDoc = await response.json();
      
      // Add the new document to the list
      setDocuments(prev => [uploadedDoc, ...prev]);
      
      // Reload analytics
      loadAnalytics();
      
      // Check for security issues and show alert if needed
      const hasSecurityIssues = uploadedDoc.detectedFlags && uploadedDoc.detectedFlags.length > 0;
      const hasCriticalIssues = uploadedDoc.detectedFlags?.some(flag => 
        flag.includes('Social Security Number') || 
        flag.includes('Credit Card') ||
        flag.includes('SSN')
      );

      if (hasCriticalIssues) {
        setSelectedDocument(uploadedDoc);
        setShowSecurityAlert(true);
        showWarning(`⚠️ SECURITY ALERT: "${file.name}" contains sensitive information!`, 8000);
      } else if (uploadedDoc.isQuarantined) {
        showWarning(`Document "${file.name}" uploaded but quarantined due to security concerns.`, 6000);
      } else if (uploadedDoc.riskLevel === 'HIGH' || uploadedDoc.riskLevel === 'CRITICAL') {
        showWarning(`Document "${file.name}" uploaded successfully but flagged as ${uploadedDoc.riskLevel.toLowerCase()} risk.`, 6000);
      } else {
        showSuccess(`Document "${file.name}" uploaded successfully and approved!`, 4000);
      }

    } catch (error) {
      console.error('Upload failed:', error);
      showError('Network error during upload. Please check your connection and try again.', 5000);
    } finally {
      setTimeout(() => {
        setIsUploading(false);
        setUploadProgress(0);
      }, 1000);
    }

    // Reset file input
    event.target.value = '';
  };

  const handleDownload = async (documentId, filename) => {
    try {
      const response = await apiCall(`/documents/${documentId}/download`);
      const blob = await response.blob();
      
      // Create download link
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      
      showSuccess(`Downloaded "${filename}" successfully.`, 3000);
    } catch (error) {
      console.error('Download failed:', error);
      showError(`Failed to download "${filename}". Please try again.`, 5000);
    }
  };

  const handleDelete = async (documentId) => {
    const document = documents.find(doc => doc.id === documentId);
    const filename = document?.originalFilename || document?.filename || 'document';
    
    if (!window.confirm(`Are you sure you want to delete "${filename}"? This action cannot be undone.`)) {
      return;
    }

    try {
      await apiCall(`/documents/${documentId}`, { method: 'DELETE' });
      setDocuments(prev => prev.filter(doc => doc.id !== documentId));
      loadAnalytics();
      showSuccess(`"${filename}" deleted successfully.`, 3000);
    } catch (error) {
      console.error('Delete failed:', error);
      showError(`Failed to delete "${filename}". Please try again.`, 5000);
    }
  };

  const handleViewDetails = (document) => {
    if (document.detectedFlags && document.detectedFlags.length > 0) {
      setSelectedDocument(document);
      setShowSecurityAlert(true);
    } else {
      showSuccess('No security issues detected in this document.', 3000);
    }
  };

  const handleLogout = () => {
    showSuccess('Logged out successfully. Stay secure!', 2000);
    setTimeout(() => {
      onLogout();
    }, 1000);
  };

  const getRiskLevelColor = (riskLevel) => {
    switch (riskLevel) {
      case 'LOW': return 'text-green-600 bg-green-100';
      case 'MEDIUM': return 'text-yellow-600 bg-yellow-100';
      case 'HIGH': return 'text-red-600 bg-red-100';
      case 'CRITICAL': return 'text-red-800 bg-red-200';
      default: return 'text-gray-600 bg-gray-100';
    }
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'APPROVED': return 'text-green-600 bg-green-100';
      case 'SCANNING': return 'text-blue-600 bg-blue-100';
      case 'ANALYZING': return 'text-purple-600 bg-purple-100';
      case 'QUARANTINED': return 'text-red-600 bg-red-100';
      case 'REJECTED': return 'text-red-800 bg-red-200';
      default: return 'text-gray-600 bg-gray-100';
    }
  };

  const formatFileSize = (bytes) => {
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    if (bytes === 0) return '0 Bytes';
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return Math.round(bytes / Math.pow(1024, i) * 100) / 100 + ' ' + sizes[i];
  };

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString();
  };

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <SnackbarContainer 
        snackbars={snackbars} 
        onRemove={removeSnackbar} 
        position="bottom-left" 
      />
      
      {/* Service Wake-Up Modal */}
      <ServiceWakeUpMessage isVisible={isServiceWaking} />
      
      {/* Security Alert Modal */}
      <SecurityAlertModal 
        isOpen={showSecurityAlert}
        onClose={() => setShowSecurityAlert(false)}
        document={selectedDocument}
      />
      
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center space-x-3">
            <Shield className="h-8 w-8 text-blue-600" />
            <h1 className="text-3xl font-bold text-gray-900">VaultGuardian AI</h1>
          </div>
          
          {/* User info and logout */}
          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-2 text-gray-600">
              <User className="h-5 w-5" />
              <div className="flex flex-col">
                <span className="font-medium text-sm">
                  {user.firstName} {user.lastName}
                </span>
                <span className="text-xs text-gray-500">
                  @{user.username}
                </span>
              </div>
            </div>
            <button
              onClick={handleLogout}
              className="flex items-center space-x-2 px-4 py-2 text-gray-600 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
            >
              <LogOut className="h-5 w-5" />
              <span>Logout</span>
            </button>
          </div>
        </div>

        {/* Analytics Dashboard */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <div className="bg-white rounded-lg shadow p-6">
            <div className="flex items-center">
              <FileText className="h-8 w-8 text-blue-600" />
              <div className="ml-4">
                <p className="text-sm font-medium text-gray-500">Total Documents</p>
                <p className="text-2xl font-bold text-gray-900">{analytics.totalDocuments}</p>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-lg shadow p-6">
            <div className="flex items-center">
              <AlertTriangle className="h-8 w-8 text-red-600" />
              <div className="ml-4">
                <p className="text-sm font-medium text-gray-500">Quarantined</p>
                <p className="text-2xl font-bold text-red-600">{analytics.quarantinedDocuments}</p>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-lg shadow p-6">
            <div className="flex items-center">
              <Shield className="h-8 w-8 text-yellow-600" />
              <div className="ml-4">
                <p className="text-sm font-medium text-gray-500">High Risk</p>
                <p className="text-2xl font-bold text-yellow-600">{analytics.highRiskDocuments}</p>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-lg shadow p-6">
            <div className="flex items-center">
              <Upload className="h-8 w-8 text-green-600" />
              <div className="ml-4">
                <p className="text-sm font-medium text-gray-500">Upload Status</p>
                <p className="text-lg font-semibold text-green-600">
                  {isUploading ? `${uploadProgress}%` : 'Ready'}
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Upload Section */}
        <div className="bg-white rounded-lg shadow p-6 mb-8">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">Upload Document</h2>
          <div className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center">
            <Upload className="mx-auto h-12 w-12 text-gray-400 mb-4" />
            <label htmlFor="file-upload" className="cursor-pointer">
              <span className="text-lg font-medium text-gray-700">
                Click to upload or drag and drop
              </span>
              <input
                id="file-upload"
                type="file"
                className="hidden"
                onChange={handleFileUpload}
                disabled={isUploading}
                accept=".pdf,.docx,.xlsx,.txt"
              />
            </label>
            <p className="text-sm text-gray-500 mt-2">
              PDF, DOCX, XLSX, TXT up to 50MB
            </p>
            {isUploading && (
              <div className="mt-4">
                <div className="bg-gray-200 rounded-full h-2">
                  <div 
                    className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                    style={{ width: `${uploadProgress}%` }}
                  ></div>
                </div>
                <p className="text-sm text-gray-600 mt-2">
                  {uploadProgress < 90 ? 'Uploading...' : 'Processing and scanning...'}
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Documents Table */}
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200">
            <h2 className="text-xl font-semibold text-gray-900">Documents</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Document
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Risk Level
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Security Issues
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Size
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Uploaded
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {documents.map((doc) => {
                  // Count specific security issues for display
                  const ssnCount = doc.detectedFlags?.filter(flag => 
                    flag.includes('Social Security Number') || flag.includes('SSN')
                  ).length || 0;
                  
                  const creditCardCount = doc.detectedFlags?.filter(flag => 
                    flag.includes('Credit Card')
                  ).length || 0;

                  return (
                    <tr key={doc.id} className={doc.isQuarantined ? 'bg-red-50' : ''}>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center">
                          <FileText className="h-5 w-5 text-gray-400 mr-3" />
                          <div>
                            <div className="text-sm font-medium text-gray-900">
                              {doc.originalFilename || doc.filename}
                            </div>
                            {doc.isQuarantined && (
                              <div className="text-xs text-red-600">⚠️ Quarantined</div>
                            )}
                            {/* Security warnings */}
                            {(ssnCount > 0 || creditCardCount > 0) && (
                              <div className="text-xs text-red-600 font-medium mt-1">
                                🚨 Sensitive Data Exposed
                              </div>
                            )}
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        {/* Processing indicator for SCANNING/ANALYZING status */}
                        {(doc.status === 'SCANNING' || doc.status === 'ANALYZING') ? (
                          <div className="flex items-center space-x-2">
                            <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-600"></div>
                            <span className="text-sm text-blue-600">Processing security scan...</span>
                          </div>
                        ) : (
                          <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${getStatusColor(doc.status)}`}>
                            {doc.status}
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${getRiskLevelColor(doc.riskLevel)}`}>
                          {doc.riskLevel}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                        {/* Check if quarantined first */}
                        {doc.isQuarantined ? (
                          <div className="space-y-1">
                            <div className="text-xs text-red-600 font-semibold bg-red-100 px-2 py-1 rounded">
                              🚨 QUARANTINED
                            </div>
                            <div className="text-xs text-red-700">
                              {doc.quarantineReason || 'Security violation detected'}
                            </div>
                          </div>
                        ) : doc.detectedFlags && doc.detectedFlags.length > 0 ? (
                          <div className="space-y-1">
                            {ssnCount > 0 && (
                              <div className="text-xs text-red-600 font-semibold bg-red-100 px-2 py-1 rounded">
                                🆔 {ssnCount} SSN{ssnCount > 1 ? 's' : ''} Exposed
                              </div>
                            )}
                            {creditCardCount > 0 && (
                              <div className="text-xs text-red-600 font-semibold bg-red-100 px-2 py-1 rounded">
                                💳 {creditCardCount} Credit Card{creditCardCount > 1 ? 's' : ''} Exposed
                              </div>
                            )}
                            {doc.detectedFlags.length - ssnCount - creditCardCount > 0 && (
                              <div className="text-xs text-yellow-600 font-semibold">
                                +{doc.detectedFlags.length - ssnCount - creditCardCount} other issue{doc.detectedFlags.length - ssnCount - creditCardCount > 1 ? 's' : ''}
                              </div>
                            )}
                          </div>
                        ) : (
                          <span className="text-green-600 text-xs font-semibold">✅ Clean</span>
                        )}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {formatFileSize(doc.fileSize)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {formatDate(doc.createdAt)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                        <div className="flex space-x-2">
                          <button 
                            className="text-blue-600 hover:text-blue-900 p-1 rounded"
                            title="View Security Details"
                            onClick={() => handleViewDetails(doc)}
                          >
                            <Eye className="h-4 w-4" />
                          </button>
                          {!doc.isQuarantined && doc.status === 'APPROVED' && (
                            <button 
                              className="text-green-600 hover:text-green-900 p-1 rounded"
                              title="Download"
                              onClick={() => handleDownload(doc.id, doc.originalFilename || doc.filename)}
                            >
                              <Download className="h-4 w-4" />
                            </button>
                          )}
                          <button 
                            className="text-red-600 hover:text-red-900 p-1 rounded"
                            title="Delete"
                            onClick={() => handleDelete(doc.id)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {documents.length === 0 && (
                  <tr>
                    <td colSpan="7" className="px-6 py-8 text-center text-gray-500">
                      No documents uploaded yet. Upload your first document above!
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Risk Distribution Chart */}
        {analytics.riskDistribution && analytics.riskDistribution.length > 0 && (
          <div className="mt-8 bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-semibold text-gray-900 mb-4">Risk Distribution</h2>
            <div className="space-y-4">
              {analytics.riskDistribution.map((item) => (
                <div key={item.riskLevel} className="flex items-center">
                  <div className="w-24 text-sm font-medium text-gray-700">
                    {item.riskLevel}
                  </div>
                  <div className="flex-1 mx-4">
                    <div className="bg-gray-200 rounded-full h-4">
                      <div 
                        className={`h-4 rounded-full ${
                          item.riskLevel === 'LOW' ? 'bg-green-500' :
                          item.riskLevel === 'MEDIUM' ? 'bg-yellow-500' : 'bg-red-500'
                        }`}
                        style={{ width: `${item.percentage}%` }}
                      ></div>
                    </div>
                  </div>
                  <div className="w-16 text-sm text-gray-600 text-right">
                    {item.count} docs
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Security Features Overview */}
        <div className="mt-8 bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">Security Features Active</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div className="flex items-center space-x-3 p-3 bg-green-50 rounded-lg">
              <div className="w-3 h-3 bg-green-500 rounded-full"></div>
              <span className="text-sm font-medium text-green-800">Malware Scanning</span>
            </div>
            <div className="flex items-center space-x-3 p-3 bg-green-50 rounded-lg">
              <div className="w-3 h-3 bg-green-500 rounded-full"></div>
              <span className="text-sm font-medium text-green-800">LLM Content Analysis</span>
            </div>
            <div className="flex items-center space-x-3 p-3 bg-green-50 rounded-lg">
              <div className="w-3 h-3 bg-green-500 rounded-full"></div>
              <span className="text-sm font-medium text-green-800">Policy Enforcement</span>
            </div>
            <div className="flex items-center space-x-3 p-3 bg-green-50 rounded-lg">
              <div className="w-3 h-3 bg-green-500 rounded-full"></div>
              <span className="text-sm font-medium text-green-800">Encrypted Storage</span>
            </div>
            <div className="flex items-center space-x-3 p-3 bg-green-50 rounded-lg">
              <div className="w-3 h-3 bg-green-500 rounded-full"></div>
              <span className="text-sm font-medium text-green-800">Access Control</span>
            </div>
            <div className="flex items-center space-x-3 p-3 bg-green-50 rounded-lg">
              <div className="w-3 h-3 bg-green-500 rounded-full"></div>
              <span className="text-sm font-medium text-green-800">Audit Logging</span>
            </div>
          </div>
        </div>

        {/* Risk Level Guide */}
        <RiskLevelGuide className="mt-8" />

        {/* Processing Status Notice */}
        {documents.some(doc => doc.status === 'SCANNING' || doc.status === 'ANALYZING') && (
          <div className="mt-8 bg-blue-50 border border-blue-200 rounded-lg p-4">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-600"></div>
              </div>
              <div className="ml-3">
                <h3 className="text-sm font-medium text-blue-800">Processing Documents</h3>
                <div className="mt-2 text-sm text-blue-700">
                  <p>VaultGuardian AI is analyzing your documents for security threats. This typically takes 5-10 seconds.</p>
                  <p className="mt-1">The dashboard will automatically refresh to show results.</p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Demo Notice */}
        <div className="mt-8 bg-blue-50 border border-blue-200 rounded-lg p-4">
          <div className="flex items-center">
            <div className="flex-shrink-0">
              <AlertTriangle className="h-5 w-5 text-blue-600" />
            </div>
            <div className="ml-3">
              <h3 className="text-sm font-medium text-blue-800">Demo Environment Notice</h3>
              <div className="mt-2 text-sm text-blue-700">
                <p>This is a demonstration environment. All uploaded documents are automatically deleted after 48 hours to manage costs.</p>
                <p className="mt-1">For production use, documents would be stored permanently with full backup and security measures.</p>
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="mt-8 text-center text-sm text-gray-500 space-y-2">
          <p className="font-medium text-gray-700">VaultGuardian AI - Protecting your documents with intelligent security</p>
          <p>Built with Spring Boot + PostgreSQL + AWS S3 + Ollama LLM + React</p>
          
          {/* Professional Contact Info */}
          <div className="pt-2 border-t border-gray-200 mt-4">
            <p className="text-xs text-gray-400">
              Demo created by Anish Kajan • 
              <a href="mailto:anishkajan2005@gmail.com" className="hover:text-blue-600 transition-colors ml-1">
                Contact for inquiries
              </a>
            </p>
            <p className="text-xs text-gray-400 mt-1">
              © 2024 VaultGuardian AI • Educational/Demo Purpose
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default VaultGuardianDashboard;
