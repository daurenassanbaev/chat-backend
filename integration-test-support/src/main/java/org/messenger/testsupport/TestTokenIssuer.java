package org.messenger.testsupport;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Local signing key and JWKS endpoint for HTTP and STOMP integration tests. */
public final class TestTokenIssuer implements AutoCloseable {
    private final RSAKey key;
    private final HttpServer server;

    public TestTokenIssuer() {
        try {
            key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
            byte[] keys = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/certs", exchange -> {
                try (exchange) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, keys.length);
                    exchange.getResponseBody().write(keys);
                }
            });
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String token(String subject) throws Exception {
        return token(subject, Instant.now().plusSeconds(600));
    }

    public String token(String subject, Instant expiration) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer()).audience("chat-api").subject(subject)
                .expirationTime(Date.from(expiration)).claim("typ", "Bearer").build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
