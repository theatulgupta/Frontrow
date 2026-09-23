package com.frontrow.identity;

import com.frontrow.config.ApiError;
import com.frontrow.config.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ATTRIBUTE = "frontrow.userId";

    private final TokenSigner tokenSigner;
    private final ObjectMapper objectMapper;

    public TokenAuthenticationFilter(TokenSigner tokenSigner, ObjectMapper objectMapper) {
        this.tokenSigner = tokenSigner;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!requiresAuth(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }
        try {
            String userId = tokenSigner.verify(header.substring("Bearer ".length()).trim());
            request.setAttribute(USER_ATTRIBUTE, userId);
            filterChain.doFilter(request, response);
        } catch (ApiException exception) {
            writeUnauthorized(response);
        }
    }

    private boolean requiresAuth(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("GET".equals(method) && path.startsWith("/api/bookings/")) {
            return true;
        }
        return "POST".equals(method) && path.matches("/api/shows/[^/]+/bookings");
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiError.from(ApiException.unauthorized()));
    }
}
