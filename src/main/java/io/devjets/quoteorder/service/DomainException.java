package io.devjets.quoteorder.service;
import org.springframework.http.HttpStatus;
public class DomainException extends RuntimeException {
 public final String code; public final HttpStatus status;
 public DomainException(String code,String message,HttpStatus status){super(message);this.code=code;this.status=status;}
 public static DomainException notFound(String what){return new DomainException("NOT_FOUND",what+" not found",HttpStatus.NOT_FOUND);}
}
