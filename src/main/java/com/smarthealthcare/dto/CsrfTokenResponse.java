package com.smarthealthcare.dto;

public record CsrfTokenResponse(String headerName, String token) {

    @Override
    public String toString() {
        return "CsrfTokenResponse[redacted]";
    }
}
