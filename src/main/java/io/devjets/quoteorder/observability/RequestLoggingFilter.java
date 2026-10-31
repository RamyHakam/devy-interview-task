package io.devjets.quoteorder.observability;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.UUID;
@Component
public class RequestLoggingFilter implements Filter {
 private static final Logger log=LoggerFactory.getLogger(RequestLoggingFilter.class);
 public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
  HttpServletRequest req=(HttpServletRequest)request;HttpServletResponse res=(HttpServletResponse)response;
  String cid=req.getHeader("X-Correlation-Id");if(cid==null||cid.isBlank())cid=UUID.randomUUID().toString();
  req.setAttribute("correlationId",cid);res.setHeader("X-Correlation-Id",cid);long start=System.nanoTime();
  try{chain.doFilter(request,response);}finally{log.info("http_request correlation_id={} method={} path={} status={} duration_ms={}",cid,req.getMethod(),req.getRequestURI(),res.getStatus(),(System.nanoTime()-start)/1_000_000);}
 }
}
