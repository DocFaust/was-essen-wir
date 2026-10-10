package de.wasessenwir.web;

import de.wasessenwir.dish.DuplicateDishException;
import de.wasessenwir.dish.DuplicateVariantException;
import de.wasessenwir.dish.InvalidRequestException;
import de.wasessenwir.dish.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException exception) {
        return problem(exception.status(), exception.getMessage(), exception.code());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> invalid(InvalidRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), exception.code());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable() {
        return problem(HttpStatus.BAD_REQUEST, "Die Eingabe ist ungültig.", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> mismatch() {
        return problem(HttpStatus.BAD_REQUEST, "Die Eingabe ist ungültig.", null);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> missing(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), null);
    }

    @ExceptionHandler(DuplicateDishException.class)
    ResponseEntity<ProblemDetail> duplicateDish(DuplicateDishException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "DUPLICATE_DISH_NAME");
    }

    @ExceptionHandler(DuplicateVariantException.class)
    ResponseEntity<ProblemDetail> duplicateVariant(DuplicateVariantException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "DUPLICATE_VARIANT_NAME");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict() {
        return problem(HttpStatus.CONFLICT, "Die Angaben widersprechen vorhandenen Daten.", null);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, String code) {
        return ResponseEntity.status(status).body(ApiProblems.of(status, ApiProblems.title(status), detail, code));
    }
}
