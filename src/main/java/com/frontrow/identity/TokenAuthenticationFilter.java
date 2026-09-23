package com.frontrow.identity;

import com.frontrow.config.ApiError;
import com.frontrow.config.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ATTRIBUTE = "frontrow.userId";

    private static final Pattern SHOW = Pattern.compile("/api/shows/[^/]+");
    private static final Pattern SEATS = Pattern.compile("/api/shows/[^/]+/seats");

    private final TokenSigner tokenSigner;
    private final ObjectMapper objectMapper;

    public TokenAuthenticationFilter(TokenSigner tokenSigner, ObjectMapper objectMapper) {
        this.tokenSigner = tokenSigner;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isPublic(request)) {
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

    private boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/actuator")) {
            return true;
        }
        if ("GET".equals(method) && ("/api/shows".equals(path) || SHOW.matcher(path).matches() || SEATS.matcher(path).matches())) {
            return true;
        }
        return "POST".equals(method) && "/api/dev/tokens".equals(path);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiError.from(ApiException.unauthorized()));
    }
}
