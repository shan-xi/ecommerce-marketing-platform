package com.example.marketingplatform.identity.application;

public class RefreshRejectedException extends RuntimeException {
    public RefreshRejectedException() {
        super("Refresh session is invalid");
    }
}
