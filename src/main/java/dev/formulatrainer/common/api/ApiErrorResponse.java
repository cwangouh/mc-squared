package dev.formulatrainer.common.api;

import java.util.List;

public record ApiErrorResponse(int status, String code, String message, List<FieldErrorResponse> fieldErrors) {

    public ApiErrorResponse(int status, String code, String message) {
        this(status, code, message, List.of());
    }
}
