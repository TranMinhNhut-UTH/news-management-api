package com.uth.news.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final SecurityErrorHandler errorHandler;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService,
                                   SecurityErrorHandler errorHandler) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.regionMatches(true, 0, "Bearer ", 0, 7)
                    || header.substring(7).isBlank()) {
                errorHandler.unauthorized(response, "Invalid Bearer token");
                return;
            }
            try {
                String username = jwtService.extractUsername(header.substring(7));
                var user = userDetailsService.loadUserByUsername(username);
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException exception) {
                SecurityContextHolder.clearContext();
                errorHandler.unauthorized(response, "Token expired");
                return;
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException exception) {
                SecurityContextHolder.clearContext();
                errorHandler.unauthorized(response, "Invalid Bearer token");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
