package com.chatbot.motosdelcaribe.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class ApiKeyFilterTest {

    private static final String VALID_KEY = "test-key-1234";
    private final ApiKeyFilter filter = new ApiKeyFilter(VALID_KEY);

    @Mock private FilterChain chain;
    private MockHttpServletRequest req;
    private MockHttpServletResponse res;

    @BeforeEach
    void setup() {
        res = new MockHttpServletResponse();
    }

    // ----- rutas no protegidas pasan sin header -----

    @Test
    void healthEndpointNoExigeHeader() throws ServletException, IOException {
        req = new MockHttpServletRequest("GET", "/actuator/health");

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200); // default; no se modifico
    }

    @Test
    void swaggerNoExigeHeader() throws ServletException, IOException {
        req = new MockHttpServletRequest("GET", "/swagger-ui.html");

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    // ----- rutas protegidas exigen header -----

    @Test
    void apiSinHeaderRetorna401YNoLlamaAlChain() throws ServletException, IOException {
        req = new MockHttpServletRequest("POST", "/api/conversation");

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("unauthorized");
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void apiConHeaderInvalidoRetorna401() throws ServletException, IOException {
        req = new MockHttpServletRequest("POST", "/api/conversation");
        req.addHeader(ApiKeyFilter.HEADER, "wrong-key");

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void apiConHeaderValidoLlamaAlChain() throws ServletException, IOException {
        req = new MockHttpServletRequest("POST", "/api/conversation");
        req.addHeader(ApiKeyFilter.HEADER, VALID_KEY);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    // ----- defensa contra rutas falsas que parecen api -----

    @Test
    void rutaQueEmpiezaConApiPeroNoEsApiSeProtege() throws ServletException, IOException {
        // /api/cualquier-cosa requiere header — el filter es prefijo-based intencionalmente
        // para que cualquier endpoint nuevo bajo /api quede protegido por defecto.
        req = new MockHttpServletRequest("GET", "/api/health");

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(req, res);
    }
}
