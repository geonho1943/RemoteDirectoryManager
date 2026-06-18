package com.example.fileserver.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsMissingRequestParameterToBadRequest() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entries");
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("path", "String");

        var response = handler.handleFrameworkException(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    }
}
