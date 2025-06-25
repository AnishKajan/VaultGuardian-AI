package com.vaultguardian.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class LLMConfig {
    
    @Value("${huggingface.api.url:https://api-inference.huggingface.co}")
    private String huggingFaceBaseUrl;
    
    // RENAMED to avoid conflict with existing webClient bean
    @Bean
    public WebClient llmWebClient() {
        return WebClient.builder()
                .baseUrl(huggingFaceBaseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB
                .build();
    }
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024));
    }
}