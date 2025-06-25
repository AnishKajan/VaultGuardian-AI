package com.vaultguardian.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    
    @Override
    public void commence(HttpServletRequest request, 
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {
        
        log.error("Unauthorized access attempt: {}", authException.getMessage());
        
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        String jsonResponse = String.format(
            "{\n" +
            "  \"error\": \"Unauthorized\",\n" +
            "  \"message\": \"Access token is missing or invalid\",\n" +
            "  \"timestamp\": \"%s\",\n" +
            "  \"path\": \"%s\"\n" +
            "}",
            Instant.now().toString(),
            request.getRequestURI()
        );
        
        response.getWriter().write(jsonResponse);
    }
}