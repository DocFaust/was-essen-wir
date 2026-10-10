package de.wasessenwir.dish;

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

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> invalid(InvalidRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Ungültige Eingabe", exception.getMessage(), exception.code());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable() {
        return problem(HttpStatus.BAD_REQUEST, "Ungültige Eingabe", "Die Eingabe ist ungültig.", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> mismatch() {
        return problem(HttpStatus.BAD_REQUEST, "Ungültige Eingabe", "Die Eingabe ist ungültig.", null);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> missing(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Nicht gefunden", exception.getMessage(), null);
    }

    @ExceptionHandler(DuplicateDishException.class)
    ResponseEntity<ProblemDetail> duplicateDish(DuplicateDishException exception) {
        return problem(HttpStatus.CONFLICT, "Bereits vorhanden", exception.getMessage(), "DUPLICATE_DISH_NAME");
    }

    @ExceptionHandler(DuplicateVariantException.class)
    ResponseEntity<ProblemDetail> duplicateVariant(DuplicateVariantException exception) {
        return problem(HttpStatus.CONFLICT, "Bereits vorhanden", exception.getMessage(), "DUPLICATE_VARIANT_NAME");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict() {
        return problem(HttpStatus.CONFLICT, "Bereits vorhanden", "Die Angaben widersprechen vorhandenen Daten.", null);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (code != null) {
            problem.setProperty("code", code);
        }
        return ResponseEntity.status(status).body(problem);
    }
}
