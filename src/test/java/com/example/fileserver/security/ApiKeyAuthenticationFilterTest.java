package com.example.fileserver.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthenticationFilterTest {

    private static final String SECRET_HASH = "2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b";

    private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
            new SecurityProperties(SECRET_HASH),
            new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void acceptsMatchingRawApiKey() throws Exception {
        MockHttpServletRequest request = request("/api/v1/auth/verify");
        request.addHeader("API-Key", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsInvalidApiKey() throws Exception {
        MockHttpServletRequest request = request("/api/v1/auth/verify");
        request.addHeader("API-Key", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED_API_KEY");
    }

    @Test
    void allowsHealthCheckWithoutApiKey() throws Exception {
        MockHttpServletRequest request = request("/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
