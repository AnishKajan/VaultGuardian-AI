package com.vaultguardian.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class AwsConfig {
    
    @Value("${AWS_ACCESS_KEY_ID}")
    private String accessKeyId;
    
    @Value("${AWS_SECRET_ACCESS_KEY}")
    private String secretAccessKey;
    
    @Value("${AWS_REGION:us-east-2}")
    private String awsRegion;
    
    @Bean
    @Primary
    public S3Client s3Client() {
        log.info("Configuring S3Client with region: {}", awsRegion);
        
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }
}