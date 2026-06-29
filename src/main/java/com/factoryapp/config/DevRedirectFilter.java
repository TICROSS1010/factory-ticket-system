package com.factoryapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// In dev, redirect all browser page requests to the Vite dev server on localhost:3000.
// Runs as a filter so it intercepts before Spring MVC's welcome-page handler serves index.html.
@Component
@Profile("dev")
@Order(1)
public class DevRedirectFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        boolean isGet = "GET".equalsIgnoreCase(request.getMethod());
        boolean isApi = uri.startsWith("/api/");
        boolean isStatic = uri.contains(".");  // .js, .css, .ico, etc.

        if (!isGet || isApi || isStatic) {
            chain.doFilter(request, response);
            return;
        }

        String query = request.getQueryString();
        String target = "http://localhost:3000" + uri + (query != null ? "?" + query : "");
        response.sendRedirect(target);
    }
}
