package com.vaultguardian.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = false)
public class AwsConfig {
    
    @Value("${aws.access-key-id:}")
    private String accessKeyId;
    
    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;
    
    @Value("${aws.region:us-east-2}")
    private String region;
    
    @Bean
    public S3Client s3Client() {
        if (accessKeyId.isEmpty() || secretAccessKey.isEmpty()) {
            // Use default credentials provider (for EC2, ECS, etc.)
            return S3Client.builder()
                    .region(Region.of(region))
                    .build();
        }
        
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
    }
    
    // Remove S3Template bean - it's causing the error
    // S3Template is not needed if you're using S3Client directly
}