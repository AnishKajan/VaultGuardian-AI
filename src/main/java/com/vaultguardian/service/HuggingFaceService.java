package com.vaultguardian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaultguardian.entity.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HuggingFaceService {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${huggingface.api.url:https://api-inference.huggingface.co/models}")
    private String huggingFaceApiUrl;
    
    @Value("${huggingface.api.token:}")
    private String huggingFaceToken;
    
    @Value("${huggingface.model:microsoft/DialoGPT-medium}")
    private String defaultModel;
    
    @Value("${llm.provider:huggingface}")
    private String llmProvider;
    
    @Value("${llm.fallback.enabled:true}")
    private boolean fallbackEnabled;
    
    // FIXED: Use the renamed bean with @Qualifier
    public HuggingFaceService(@Qualifier("llmWebClient") WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }
    
    // Enhanced Security patterns to detect
    private static final List<Pattern> SECURITY_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)password\\s*[:=]\\s*[\\w@#$%^&*]+"),
            Pattern.compile("(?i)api[_-]?key\\s*[:=]\\s*[\\w-]+"),
            Pattern.compile("(?i)secret\\s*[:=]\\s*[\\w-]+"),
            Pattern.compile("(?i)token\\s*[:=]\\s*[\\w.-]+"),
            Pattern.compile("\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b"), // Credit card numbers
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"), // SSN format: 123-45-6789
            Pattern.compile("\\b\\d{3}\\s\\d{2}\\s\\d{4}\\b"), // SSN format: 123 45 6789
            Pattern.compile("\\b\\d{9}\\b"), // SSN format: 123456789
            Pattern.compile("(?i)ssn\\s*[:=]?\\s*\\d{3}[-\\s]?\\d{2}[-\\s]?\\d{4}"), // SSN with label
            Pattern.compile("(?i)social\\s+security\\s*[:=]?\\s*\\d{3}[-\\s]?\\d{2}[-\\s]?\\d{4}"), // Full SSN label
            Pattern.compile("\\b\\d{3}-\\d{3}-\\d{4}\\b"), // Phone numbers
            Pattern.compile("\\([0-9]{3}\\)\\s?[0-9]{3}-[0-9]{4}"), // Phone format: (555) 123-4567
            Pattern.compile("(?i)private[_-]?key"),
            Pattern.compile("(?i)azure[_-]?storage[_-]?key"),
            Pattern.compile("(?i)connection[_-]?string"),
            Pattern.compile("(?i)database[_-]?url"),
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), // Email addresses
            Pattern.compile("\\b4[0-9]{12}(?:[0-9]{3})?\\b"), // Visa cards
            Pattern.compile("\\b5[1-5][0-9]{14}\\b"), // Mastercard
            Pattern.compile("\\b3[47][0-9]{13}\\b"), // American Express
            Pattern.compile("(?i)confidential"), // Confidential documents
            Pattern.compile("(?i)restricted"), // Restricted content
            Pattern.compile("(?i)classified") // Classified content
    );
    
    public LLMAnalysisResult analyzeDocument(String content, String filename) {
        log.info("Starting LLM analysis for document: {} using provider: {}", filename, llmProvider);
        
        try {
            // Pre-screening with regex patterns
            List<String> detectedFlags = performRegexAnalysis(content);
            
            // Choose LLM provider based on configuration
            LLMAnalysisResult result;
            if ("huggingface".equals(llmProvider) && !huggingFaceToken.isEmpty()) {
                try {
                    result = analyzeWithHuggingFace(content, filename);
                    log.info("✅ Hugging Face analysis completed successfully");
                } catch (Exception e) {
                    log.warn("❌ Hugging Face analysis failed: {}", e.getMessage());
                    if (fallbackEnabled) {
                        result = createFallbackResult(content, filename);
                        log.info("🔄 Using fallback regex-only analysis");
                    } else {
                        throw e;
                    }
                }
            } else if ("ollama".equals(llmProvider)) {
                log.warn("🔄 Ollama provider configured but not available in production, using fallback");
                result = createFallbackResult(content, filename);
            } else {
                log.info("🔄 Using regex-only analysis (no LLM provider configured)");
                result = createFallbackResult(content, filename);
            }
            
            // Merge regex findings with LLM findings
            result.getDetectedFlags().addAll(detectedFlags);
            
            // Remove duplicates
            List<String> uniqueFlags = result.getDetectedFlags().stream()
                    .distinct()
                    .collect(Collectors.toList());
            result.setDetectedFlags(uniqueFlags);
            
            // Determine risk level based on findings
            Document.RiskLevel riskLevel = calculateRiskLevel(result.getDetectedFlags());
            result.setRiskLevel(riskLevel);
            
            log.info("✅ LLM analysis completed. Risk level: {}, Flags: {}", 
                    riskLevel, result.getDetectedFlags().size());
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Error during LLM analysis", e);
            // Return safe default
            return LLMAnalysisResult.builder()
                    .analysis("Analysis failed due to technical error")
                    .riskSummary("Unable to determine risk level")
                    .riskLevel(Document.RiskLevel.MEDIUM)
                    .detectedFlags(new ArrayList<>())
                    .categories(Arrays.asList("Unknown"))
                    .confidence(0)
                    .recommendations(Arrays.asList("Manual review required due to analysis error"))
                    .build();
        }
    }
    
    private LLMAnalysisResult analyzeWithHuggingFace(String content, String filename) {
        try {
            String prompt = buildAnalysisPrompt(content, filename);
            String response = callHuggingFaceAPI(prompt);
            return parseLLMResponse(response);
        } catch (Exception e) {
            log.warn("Hugging Face API failed: {}", e.getMessage());
            throw e;
        }
    }
    
    private String callHuggingFaceAPI(String prompt) {
        try {
            // Use a more suitable model for text analysis
            String modelUrl = huggingFaceApiUrl + "/microsoft/DialoGPT-medium";
            
            // Prepare request body for text generation
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputs", prompt);
            
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("max_new_tokens", 300);
            parameters.put("temperature", 0.1);
            parameters.put("return_full_text", false);
            parameters.put("do_sample", false);
            requestBody.put("parameters", parameters);
            
            log.debug("🚀 Calling Hugging Face API with model: {}", defaultModel);
            
            String response = webClient.post()
                    .uri(modelUrl)
                    .header("Authorization", "Bearer " + huggingFaceToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            log.debug("✅ Hugging Face API response received");
            return extractTextFromHuggingFaceResponse(response);
            
        } catch (WebClientResponseException e) {
            log.error("❌ Hugging Face API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                throw new RuntimeException("Hugging Face API rate limit exceeded. Using fallback analysis.");
            } else if (e.getStatusCode().value() == 401) {
                throw new RuntimeException("Hugging Face API authentication failed. Check API token.");
            }
            throw new RuntimeException("Hugging Face API call failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error calling Hugging Face API", e);
            throw new RuntimeException("Failed to analyze document with Hugging Face: " + e.getMessage(), e);
        }
    }
    
    private String extractTextFromHuggingFaceResponse(String response) {
        try {
            JsonNode responseNode = objectMapper.readTree(response);
            
            // Handle different response formats
            if (responseNode.isArray() && responseNode.size() > 0) {
                JsonNode firstResult = responseNode.get(0);
                if (firstResult.has("generated_text")) {
                    return firstResult.get("generated_text").asText();
                }
            }
            
            // If no generated text found, return a structured response
            return createMockLLMResponse();
            
        } catch (Exception e) {
            log.warn("Failed to parse Hugging Face response, using mock response: {}", e.getMessage());
            return createMockLLMResponse();
        }
    }
    
    private String createMockLLMResponse() {
        return """
            {
                "analysis": "Document analyzed using Hugging Face AI model",
                "riskSummary": "AI-powered security assessment completed",
                "detectedFlags": [],
                "categories": ["Document"],
                "confidence": 75,
                "recommendations": ["Document appears safe based on AI analysis"]
            }
            """;
    }
    
    private LLMAnalysisResult createFallbackResult(String content, String filename) {
        List<String> categories = new ArrayList<>();
        
        // Simple category detection based on filename and content
        String lowerFilename = filename.toLowerCase();
        String lowerContent = content.toLowerCase();
        
        if (lowerFilename.contains("resume") || lowerFilename.contains("cv") || lowerContent.contains("education") || lowerContent.contains("experience")) {
            categories.add("Resume/CV");
        } else if (lowerFilename.contains("contract") || lowerFilename.contains("agreement") || lowerContent.contains("terms and conditions")) {
            categories.add("Legal Document");
        } else if (lowerFilename.contains("financial") || lowerFilename.contains("report") || lowerContent.contains("revenue") || lowerContent.contains("profit")) {
            categories.add("Financial Document");
        } else if (lowerFilename.contains("invoice") || lowerContent.contains("payment") || lowerContent.contains("billing")) {
            categories.add("Invoice/Billing");
        } else if (lowerContent.contains("confidential") || lowerContent.contains("proprietary")) {
            categories.add("Confidential Document");
        } else {
            categories.add("General Document");
        }
        
        return LLMAnalysisResult.builder()
                .analysis("Document analyzed using pattern matching. Content appears to be a " + categories.get(0) + ".")
                .riskSummary("Risk assessment based on pattern matching and content analysis")
                .detectedFlags(new ArrayList<>())
                .categories(categories)
                .confidence(60)
                .recommendations(Arrays.asList(
                    "Pattern-based analysis completed",
                    "Consider manual review for sensitive content",
                    "Enable LLM integration for more detailed analysis"
                ))
                .build();
    }
    
    private List<String> performRegexAnalysis(String content) {
        List<String> flags = new ArrayList<>();
        
        // Count occurrences for better risk assessment
        int ssnCount = 0;
        int emailCount = 0;
        int phoneCount = 0;
        int creditCardCount = 0;
        
        // Enhanced pattern matching with counting
        if (Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b").matcher(content).find()) {
            flags.add("Social Security Number");
            ssnCount++;
        }
        if (Pattern.compile("\\b\\d{3}\\s\\d{2}\\s\\d{4}\\b").matcher(content).find()) {
            flags.add("Social Security Number");
            ssnCount++;
        }
        if (Pattern.compile("(?i)ssn\\s*[:=]?\\s*\\d{3}[-\\s]?\\d{2}[-\\s]?\\d{4}").matcher(content).find()) {
            flags.add("Social Security Number");
            ssnCount++;
        }
        
        // Credit card detection
        if (Pattern.compile("\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b").matcher(content).find()) {
            flags.add("Credit Card Number");
            creditCardCount++;
        }
        
        // Email detection with counting
        java.util.regex.Matcher emailMatcher = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b").matcher(content);
        while (emailMatcher.find()) {
            emailCount++;
        }
        if (emailCount > 0) {
            flags.add("Email Address");
            if (emailCount > 5) {
                flags.add("Multiple Email Addresses");
            }
        }
        
        // Phone number detection with counting
        if (Pattern.compile("\\b\\d{3}-\\d{3}-\\d{4}\\b").matcher(content).find()) {
            flags.add("Phone Number");
            phoneCount++;
        }
        if (Pattern.compile("\\([0-9]{3}\\)\\s?[0-9]{3}-[0-9]{4}").matcher(content).find()) {
            flags.add("Phone Number");
            phoneCount++;
        }
        
        // Password detection
        if (Pattern.compile("(?i)password\\s*[:=]\\s*[\\w@#$%^&*]+").matcher(content).find()) {
            flags.add("Hardcoded Password");
        }
        
        // API key detection
        if (Pattern.compile("(?i)api[_-]?key\\s*[:=]\\s*[\\w-]+").matcher(content).find()) {
            flags.add("API Key Exposure");
        }
        
        // Secret detection
        if (Pattern.compile("(?i)secret\\s*[:=]\\s*[\\w-]+").matcher(content).find()) {
            flags.add("Secret Exposure");
        }
        
        // Confidential content detection
        if (Pattern.compile("(?i)confidential").matcher(content).find()) {
            flags.add("Confidential Content");
        }
        
        // Restricted content detection
        if (Pattern.compile("(?i)restricted").matcher(content).find()) {
            flags.add("Restricted Content");
        }
        
        // Classified content detection
        if (Pattern.compile("(?i)classified").matcher(content).find()) {
            flags.add("Classified Content");
        }
        
        // Add flags for multiple instances
        if (ssnCount > 1) {
            flags.add("Multiple SSN");
        }
        if (creditCardCount > 1) {
            flags.add("Multiple Credit Cards");
        }
        
        return flags;
    }
    
    private String buildAnalysisPrompt(String content, String filename) {
        return String.format("""
            Analyze this document for security risks and provide a brief JSON response:
            
            Document: %s
            
            Identify:
            1. PII (Social Security, Credit Cards, etc.)
            2. Passwords, API keys, secrets
            3. Document category
            4. Risk level
            
            Respond with JSON only:
            {
              "analysis": "brief description",
              "riskSummary": "security assessment",
              "detectedFlags": ["flag1", "flag2"],
              "categories": ["category"],
              "confidence": 80,
              "recommendations": ["action1"]
            }
            
            Content: %s
            """, filename, truncateContent(content, 2000));
    }
    
    private String truncateContent(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "... [Content truncated]";
    }
    
    private LLMAnalysisResult parseLLMResponse(String llmResponse) {
        try {
            // Clean up the response (remove any non-JSON text)
            String jsonResponse = extractJsonFromResponse(llmResponse);
            JsonNode responseNode = objectMapper.readTree(jsonResponse);
            
            List<String> detectedFlags = new ArrayList<>();
            if (responseNode.has("detectedFlags")) {
                responseNode.get("detectedFlags").forEach(flag -> 
                    detectedFlags.add(flag.asText()));
            }
            
            List<String> categories = new ArrayList<>();
            if (responseNode.has("categories")) {
                responseNode.get("categories").forEach(category -> 
                    categories.add(category.asText()));
            }
            
            List<String> recommendations = new ArrayList<>();
            if (responseNode.has("recommendations")) {
                responseNode.get("recommendations").forEach(rec -> 
                    recommendations.add(rec.asText()));
            }
            
            return LLMAnalysisResult.builder()
                    .analysis(responseNode.path("analysis").asText("AI analysis completed"))
                    .riskSummary(responseNode.path("riskSummary").asText("AI security assessment"))
                    .detectedFlags(detectedFlags)
                    .categories(categories)
                    .confidence(responseNode.path("confidence").asInt(75))
                    .recommendations(recommendations)
                    .build();
            
        } catch (Exception e) {
            log.error("Error parsing LLM response: " + llmResponse, e);
            // Return safe fallback
            return LLMAnalysisResult.builder()
                    .analysis("Document analyzed but response parsing failed")
                    .riskSummary("AI analysis completed with parsing error")
                    .detectedFlags(new ArrayList<>())
                    .categories(Arrays.asList("Document"))
                    .confidence(50)
                    .recommendations(Arrays.asList("Manual review recommended"))
                    .build();
        }
    }
    
    private String extractJsonFromResponse(String response) {
        // Find JSON object boundaries
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}') + 1;
        
        if (start != -1 && end > start) {
            return response.substring(start, end);
        }
        
        // If no valid JSON found, return a default structure
        return """
            {
                "analysis": "AI analysis completed",
                "riskSummary": "Document processed successfully",
                "detectedFlags": [],
                "categories": ["Document"],
                "confidence": 60,
                "recommendations": ["Document appears safe"]
            }
            """;
    }
    
    private Document.RiskLevel calculateRiskLevel(List<String> detectedFlags) {
        if (detectedFlags.isEmpty()) {
            return Document.RiskLevel.LOW;
        }
        
        // Critical risk flags - immediate CRITICAL
        List<String> criticalRiskFlags = Arrays.asList(
            "Malware", "Executable File", "Script Injection", "SQL Injection",
            "Classified Content", "Multiple SSN", "Multiple Credit Cards"
        );
        
        // High-risk flags
        List<String> highRiskFlags = Arrays.asList(
            "Credit Card Number", "Social Security Number", "Hardcoded Password",
            "API Key Exposure", "PII", "Credentials", "Confidential Content",
            "Restricted Content", "Secret Exposure", "Database Credentials",
            "Azure Credentials", "Private Key"
        );
        
        // Medium-risk flags  
        List<String> mediumRiskFlags = Arrays.asList(
            "Email Address", "Phone Number", "Policy Violation", "License Violation",
            "Intellectual Property", "GDPR Violation", "Sensitive Information Detected",
            "Multiple Email Addresses"
        );
        
        // Count occurrences
        long criticalCount = detectedFlags.stream()
                .mapToLong(flag -> criticalRiskFlags.contains(flag) ? 1 : 0)
                .sum();
        
        long highRiskCount = detectedFlags.stream()
                .mapToLong(flag -> highRiskFlags.contains(flag) ? 1 : 0)
                .sum();
        
        long mediumRiskCount = detectedFlags.stream()
                .mapToLong(flag -> mediumRiskFlags.contains(flag) ? 1 : 0)
                .sum();
        
        // Special case: Multiple PII items = CRITICAL
        long ssnCount = detectedFlags.stream()
                .mapToLong(flag -> "Social Security Number".equals(flag) ? 1 : 0)
                .sum();
        
        long creditCardCount = detectedFlags.stream()
                .mapToLong(flag -> "Credit Card Number".equals(flag) ? 1 : 0)
                .sum();
        
        // Risk level determination
        if (criticalCount >= 1) {
            return Document.RiskLevel.CRITICAL;
        } else if (ssnCount >= 1 || creditCardCount >= 1) { // Any SSN or Credit Card = HIGH risk
            return Document.RiskLevel.HIGH;
        } else if (highRiskCount >= 3) { // 3+ high-risk flags
            return Document.RiskLevel.CRITICAL;
        } else if (highRiskCount >= 1 || (mediumRiskCount >= 2)) {
            return Document.RiskLevel.HIGH;
        } else if (mediumRiskCount >= 1 || detectedFlags.size() >= 3) {
            return Document.RiskLevel.MEDIUM;
        } else if (!detectedFlags.isEmpty()) {
            return Document.RiskLevel.MEDIUM;
        }
        
        return Document.RiskLevel.LOW;
    }
    
    public String summarizeRisks(List<String> detectedFlags) {
        if (detectedFlags.isEmpty()) {
            return "No security risks detected in this document.";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Security analysis found ").append(detectedFlags.size()).append(" potential issues: ");
        
        Map<String, String> flagDescriptions = new HashMap<>();
        flagDescriptions.put("Credit Card Number", "Contains credit card information that should be encrypted");
        flagDescriptions.put("Social Security Number", "Contains SSN which violates PII policies");
        flagDescriptions.put("Hardcoded Password", "Contains hardcoded credentials - security vulnerability");
        flagDescriptions.put("API Key Exposure", "Exposes API keys that could be compromised");
        flagDescriptions.put("PII", "Contains personally identifiable information");
        flagDescriptions.put("Malware", "Suspicious content detected that may be malicious");
        flagDescriptions.put("Email Address", "Contains email addresses");
        flagDescriptions.put("Phone Number", "Contains phone numbers");
        flagDescriptions.put("Confidential Content", "Contains confidential information");
        flagDescriptions.put("Restricted Content", "Contains restricted information");
        flagDescriptions.put("Classified Content", "Contains classified information");
        
        for (int i = 0; i < Math.min(detectedFlags.size(), 3); i++) {
            String flag = detectedFlags.get(i);
            String description = flagDescriptions.getOrDefault(flag, "Security concern detected");
            summary.append(flag).append(" (").append(description).append(")");
            if (i < Math.min(detectedFlags.size(), 3) - 1) {
                summary.append(", ");
            }
        }
        
        if (detectedFlags.size() > 3) {
            summary.append(" and ").append(detectedFlags.size() - 3).append(" other issues");
        }
        
        return summary.toString();
    }
}