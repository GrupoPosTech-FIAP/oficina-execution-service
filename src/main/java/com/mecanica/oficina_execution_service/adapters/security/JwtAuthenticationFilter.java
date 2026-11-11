package com.mecanica.oficina_execution_service.adapters.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String GATEWAY_AUTH_HEADER = "x-gateway-auth";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extrairToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtService.tokenValido(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                String subject = jwtService.extrairSubject(token);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        subject,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception ignored) {
            // Token malformado, expirado ou com assinatura inválida: segue sem autenticar no contexto
        }

        filterChain.doFilter(request, response);
    }

    private String extrairToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && !authHeader.isBlank()) {
            return removerPrefixoBearer(authHeader);
        }

        String gatewayHeader = request.getHeader(GATEWAY_AUTH_HEADER);
        if (gatewayHeader != null && !gatewayHeader.isBlank()) {
            return removerPrefixoBearer(gatewayHeader);
        }

        return null;
    }

    private String removerPrefixoBearer(String valor) {
        String limpo = valor.trim();
        if (limpo.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = limpo.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return limpo;
    }
}

