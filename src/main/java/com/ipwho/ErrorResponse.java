package com.ipwho;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorResponse {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    /** Error code, for example {@code invalid_api_key}. Auth errors send this and no message. */
    @JsonProperty("error")
    private String error;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getError() { return error; }

    public void setSuccess(boolean success) { this.success = success; }
    public void setMessage(String message) { this.message = message; }
    public void setError(String error) { this.error = error; }
}
