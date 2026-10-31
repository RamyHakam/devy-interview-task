package io.devjets.quoteorder.api;
import io.devjets.quoteorder.api.ApiModels.ErrorResponse;
import io.devjets.quoteorder.service.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(DomainException.class) ResponseEntity<ErrorResponse> domain(DomainException e,HttpServletRequest r){return ResponseEntity.status(e.status).body(body(e.code,e.getMessage(),r));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e,HttpServletRequest r){return ResponseEntity.badRequest().body(body("VALIDATION_ERROR","Request validation failed",r));}
 @ExceptionHandler(Exception.class) ResponseEntity<ErrorResponse> other(Exception e,HttpServletRequest r){return ResponseEntity.status(500).body(body("INTERNAL_ERROR","Unexpected server error",r));}
 private ErrorResponse body(String c,String m,HttpServletRequest r){return new ErrorResponse(c,m,(String)r.getAttribute("correlationId"),Instant.now());}
}
