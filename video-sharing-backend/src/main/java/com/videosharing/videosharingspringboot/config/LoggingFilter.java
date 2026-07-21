package com.videosharing.videosharingspringboot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;

@Component
@Order(1) // Ensure this filter runs first
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequest(requestWrapper);
            logResponse(responseWrapper, duration);
            responseWrapper.copyBodyToResponse(); // IMPORTANT: copy content of responseWrapper to original response
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) throws UnsupportedEncodingException {
        StringBuilder msg = new StringBuilder();
        msg.append("REQUEST:\n");
        msg.append("  Method: ").append(request.getMethod()).append("\n");
        msg.append("  URI: ").append(request.getRequestURI());
        if (request.getQueryString() != null) {
            msg.append("?").append(request.getQueryString());
        }
        msg.append("\n");
        msg.append("  Headers:\n");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            msg.append("    ").append(headerName).append(": ").append(request.getHeader(headerName)).append("\n");
        }

        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            msg.append("  Body: ").append(new String(content, request.getCharacterEncoding())).append("\n");
        }
        LOGGER.info(msg.toString());
    }

    private void logResponse(ContentCachingResponseWrapper response, long duration) throws IOException {
        StringBuilder msg = new StringBuilder();
        msg.append("RESPONSE:\n");
        msg.append("  Status: ").append(response.getStatus()).append("\n");
        msg.append("  Duration: ").append(duration).append("ms\n");
        msg.append("  Headers:\n");
        response.getHeaderNames().forEach(headerName -> {
            msg.append("    ").append(headerName).append(": ").append(response.getHeader(headerName)).append("\n");
        });

        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            msg.append("  Body: ").append(new String(content, response.getCharacterEncoding())).append("\n");
        }
        LOGGER.info(msg.toString());
    }
}
