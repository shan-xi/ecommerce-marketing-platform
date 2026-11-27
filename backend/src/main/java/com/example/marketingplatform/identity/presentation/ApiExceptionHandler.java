package com.example.marketingplatform.identity.presentation;

import com.example.marketingplatform.audience.application.AudienceSegmentService;
import com.example.marketingplatform.audience.domain.InvalidAudienceRuleException;
import com.example.marketingplatform.identity.application.AuthenticationException;
import com.example.marketingplatform.identity.application.RefreshRejectedException;
import com.example.marketingplatform.member.application.MemberProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail authentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", "UNAUTHENTICATED",
                "Email or password is invalid, or the account is inactive.");
    }

    @ExceptionHandler(RefreshRejectedException.class)
    public ProblemDetail refresh(RefreshRejectedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Refresh session is invalid", "UNAUTHENTICATED",
                "Please sign in again.");
    }

    @ExceptionHandler(InvalidAudienceRuleException.class)
    public ProblemDetail invalidAudienceRule(InvalidAudienceRuleException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid audience rule", "INVALID_AUDIENCE_RULE", ex.getMessage());
    }

    @ExceptionHandler({AudienceSegmentService.AudienceResourceNotFoundException.class, MemberProfileService.MemberProfileNotFoundException.class})
    public ProblemDetail resourceNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", "RESOURCE_NOT_FOUND", "The requested resource does not exist or is not visible.");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ProblemDetail malformedRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_ERROR", "The request body is invalid.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail validation(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "INTERNAL_ERROR",
                "The request could not be completed.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setType(URI.create("about:blank"));
        p.setTitle(title);
        p.setProperty("code", code);
        p.setProperty("traceId", java.util.UUID.randomUUID().toString());
        return p;
    }
}
