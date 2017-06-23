package com.ericsson.ntf.ext.webservices;

public class NtfRestException extends Exception {

    private String errorCode;
    private String message;

    public NtfRestException(String code, String msg) {
	errorCode = code;
	message = msg;
    }

    public String getErrorCode() {
	return errorCode;
    }

    @Override
    public String getMessage() {
	return message;
    }

}
