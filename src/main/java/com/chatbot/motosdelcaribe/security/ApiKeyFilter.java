package com.chatbot.motosdelcaribe.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Auth simple por header compartido: cada request a {@code /api/**} debe traer
 * {@code X-API-Key} con el mismo valor que la propiedad {@code app.api-key}.
 * El resto de rutas (notablemente {@code /actuator/health} y la documentacion
 * Swagger) quedan abiertas para que Render/Azure puedan probar el liveness sin
 * conocer la key.
 *
 * No se usa Spring Security porque solo hace falta validar un header; sumar
 * Spring Security agregaria 2 jars y un layer de configuracion innecesario.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private static final String PROTECTED_PREFIX = "/api/";

    private final String expectedKey;

    public ApiKeyFilter(@Value("${app.api-key}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (!req.getRequestURI().startsWith(PROTECTED_PREFIX)) {
            chain.doFilter(req, res);
            return;
        }

        String got = req.getHeader(HEADER);
        if (got == null || !got.equals(expectedKey)) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"missing or invalid X-API-Key\"}");
            return;
        }

        chain.doFilter(req, res);
    }
}
