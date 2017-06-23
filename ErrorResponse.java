package com.ericsson.ntf.ext.webservices;

import org.jboss.resteasy.core.ServerResponse;

public class ErrorResponse extends ServerResponse {
    // "<HTTP Response Code>"
    private int status;
    // "<Error-Code>"
    private String code;
    // "<Error Message>"
    private String message;

    // "<Error Details>"
    private String details;

    @Override
    public int getStatus() {
	return status;
    }

    @Override
    public void setStatus(int status) {
	this.status = status;
    }

    public String getCode() {
	return code;
    }

    public void setCode(String code) {
	this.code = code;
    }

    public String getMessage() {
	return message;
    }

    public void setMessage(String message) {
	this.message = message;
    }

    public String getDetails() {
	return details;
    }

    public void setDetails(String details) {
	this.details = details;
    }

}
