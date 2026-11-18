package dev.formulatrainer.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.formulatrainer.common.api.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionIsLoggedWithoutExposingInternalDetails(CapturedOutput output) {
        IllegalStateException exception = new IllegalStateException("Sensitive internal failure");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/media");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
            .isNotNull()
            .extracting(ApiErrorResponse::code, ApiErrorResponse::message)
            .containsExactly("INTERNAL_ERROR", "Unexpected server error");
        assertThat(output).contains("Unexpected server error");
        assertThat(output).contains("method=POST, path=/api/v1/media");
        assertThat(output).contains("Sensitive internal failure");
    }

    @Test
    void handledClientErrorIsLoggedWithRequestContext(CapturedOutput output) {
        ResourceNotFoundException exception = new ResourceNotFoundException("Image not found");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/media/missing");

        handler.handleResourceNotFound(exception, request);

        assertThat(output).contains("Handled request error");
        assertThat(output).contains("method=GET, path=/api/v1/media/missing, status=404, code=NOT_FOUND");
        assertThat(output).contains("exception=ResourceNotFoundException, reason=Image not found");
    }

}
