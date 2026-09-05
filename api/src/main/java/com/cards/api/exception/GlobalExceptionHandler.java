package com.cards.api.exception;

import com.cards.api.exception.domain.DomainException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ProblemDetail> handleExpiredJwtException(ExpiredJwtException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
            "The access token has expired. Please log in again."
        );
        pd.setTitle("Expired Token");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    @ExceptionHandler(MalformedJwtException.class)
    public ResponseEntity<ProblemDetail> handleMalformedJwtException(MalformedJwtException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setTitle("Invalid Token");
        pd.setDetail("The access token format is not valid.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<ProblemDetail> handleSignatureException(SignatureException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
            "The access token signature is not valid."
        );
        pd.setTitle("Invalid Token");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex,
                                                                       HttpServletRequest request) {
        // Log the error for debugging
        log.warn("Authentication error: {} - URI: {}", ex.getMessage(), request.getRequestURI());

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        pd.setInstance(URI.create(request.getRequestURI()));

        if (ex instanceof BadCredentialsException) {
            pd.setTitle("Invalid Credentials");
            pd.setDetail("The username or password is incorrect.");
        } else if (ex instanceof InsufficientAuthenticationException) {
            pd.setTitle("Authentication Required");
            pd.setDetail("The access token is missing or invalid.");
        } else {
            pd.setTitle("Authentication Error");
            pd.setDetail(ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
        pd.setTitle("Access Denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
    }

    // ============================================================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEntityNotFoundException(ResourceNotFoundException ex,
                                                                       HttpServletRequest request) {

        log.warn("Resource not found: {} - URI: {}", ex.getMessage(), request.getRequestURI());

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/404"));
        pd.setInstance(URI.create(request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex,
                                                                   HttpServletRequest request) {

        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));

        log.warn("Validation error: {} - URI: {}", detail, request.getRequestURI());

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation error");
        pd.setDetail(detail);
        pd.setType(URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/400"));
        pd.setInstance(URI.create(request.getRequestURI()));

        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ProblemDetail> handleMissingCookie(MissingRequestCookieException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Missing cookie");
        pd.setDetail("The required cookie '" + ex.getCookieName() + "' is missing");
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        var pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid parameter");
        pd.setDetail("Invalid value '" + ex.getValue() + "' for parameter '" + ex.getPropertyName() + "'");
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(DomainException ex,
                                                                  HttpServletRequest request) {

        log.warn("Business error: {} - URI: {}", ex.getMessage(), request.getRequestURI());

        ProblemDetail pd = ProblemDetail.forStatus(ex.getStatus());
        pd.setTitle("Business rule violation");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setInstance(URI.create(request.getRequestURI()));

        return ResponseEntity.status(ex.getStatus()).body(pd);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyRequestsException(TooManyRequestsException ex,
                                                                        HttpServletRequest request) {

        log.warn("Rate limit exceeded: {} - URI: {}", ex.getMessage(), request.getRequestURI());

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        pd.setTitle("Too Many Requests");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/429"));
        pd.setInstance(URI.create(request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(pd);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(
        ObjectOptimisticLockingFailureException ex,
        HttpServletRequest request) {

        log.warn("Optimistic lock failure: {} - URI: {}", ex.getMessage(), request.getRequestURI());

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Conflict");
        pd.setDetail("The resource was modified by another request. Please retry.");
        pd.setType(URI.create("https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/409"));
        pd.setInstance(URI.create(request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    /**
     * Fallback
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex,
                                                       HttpServletRequest request) {

        log.error("Internal error in {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal error");
        pd.setDetail("An unexpected error has occurred");
        pd.setType(URI.create("about:blank"));
        pd.setInstance(URI.create(request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
    }
}
