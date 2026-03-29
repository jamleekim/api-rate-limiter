package jaemin.ai.ratelimiter.resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientKeyResolver {
    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = (forwardedFor != null) ? forwardedFor.split(",")[0].trim() : request.getRemoteAddr();
        return ip + ":" + request.getMethod() + ":" + request.getRequestURI();
    }
}
