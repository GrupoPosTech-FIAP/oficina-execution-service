package com.mecanica.oficina_execution_service.adapters.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private final String secret;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.secret = secret;
    }

    public String extrairSubject(String token) {
        return extrairClaim(token, Claims::getSubject);
    }

    public boolean tokenValido(String token) {
        String subject = extrairSubject(token);
        return subject != null && !subject.isBlank() && !tokenExpirado(token);
    }

    private boolean tokenExpirado(String token) {
        Date expiration = extrairClaim(token, Claims::getExpiration);
        return expiration != null && expiration.before(new Date());
    }

    private <T> T extrairClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(getChave())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }

    private SecretKey getChave() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}

