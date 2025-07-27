import React from 'react';
import { Shield, AlertTriangle, AlertCircle, XCircle } from 'lucide-react';

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
            <span><strong>Upload:</strong> File uploaded to secure Azure Blob storage</span>
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

export default RiskLevelGuide;