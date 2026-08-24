/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.amazon.aws.partners.saasfactory.cognito;

import com.amazon.aws.partners.saasfactory.exception.JwtProcessingException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class JwtClaimsExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtClaimsExtractor.class);
    private static final Pattern BEARER_TOKEN_REGEX = Pattern.compile("^[B|b]earer +");
    private static final Pattern TENANT_ID_REGEX = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");
    private static final String ID_TOKEN_USE = "id";
    private static final String RS256 = "RS256";

    private final String trustedIssuer;
    private final String trustedAudience;
    private final RSAKeyProvider keyProvider;

    public JwtClaimsExtractor(String trustedIssuer, String trustedAudience) {
        this(trustedIssuer, trustedAudience, new CognitoRSAKeyProvider(requireConfiguration(
                trustedIssuer, "Trusted Cognito issuer")));
    }

    /**
     * Allows callers and tests to supply a key provider while retaining all issuer, audience, algorithm,
     * time-bound, and token-use checks.
     */
    public JwtClaimsExtractor(String trustedIssuer, String trustedAudience, RSAKeyProvider keyProvider) {
        this.trustedIssuer = requireConfiguration(trustedIssuer, "Trusted Cognito issuer");
        this.trustedAudience = requireConfiguration(trustedAudience, "Trusted Cognito app client ID");
        if (keyProvider == null) {
            throw new JwtProcessingException("A trusted RSA key provider is required.");
        }
        this.keyProvider = keyProvider;
    }

    public Map<String, Claim> getClaims(Map<String, String> request) {
        return verify(getBearerToken(request)).getClaims();
    }

    public CognitoClaims getClaims(Map<String, String> request, String tenantClaim, String trustedIdentityPool) {
        String bearerToken = getBearerToken(request);
        Map<String, Claim> claims = verify(bearerToken).getClaims();
        return CognitoClaims.builder()
                .identityPool(requireConfiguration(trustedIdentityPool, "Trusted Cognito identity pool ID"))
                .providerLogins(Collections.singletonMap(getTrustedProvider(), bearerToken))
                .tenant(getTenantId(claims, tenantClaim))
                .build();
    }

    public String getTenantId(Map<String, Claim> claims, String claimName) {
        String tenantId = null;
        Claim tenantClaim = claims.get(claimName);
        if (tenantClaim != null) {
            tenantId = tenantClaim.asString();
        }
        if (tenantId == null || !TENANT_ID_REGEX.matcher(tenantId).matches()) {
            throw new JwtProcessingException("Token tenant id is missing or contains unsafe characters.");
        }
        return tenantId;
    }

    private String getTrustedProvider() {
        return trustedIssuer.substring("https://".length());
    }

    private String getBearerToken(Map<String, String> request) {
        String bearerToken = null;
        if (request != null) {
            if (request.containsKey("Authorization")) {
                bearerToken = request.get("Authorization");
            } else if (request.containsKey("authorization")) {
                bearerToken = request.get("authorization");
            }
        }
        if (bearerToken == null) {
            throw new JwtProcessingException("Request does not contain an Authorization header.");
        }

        String[] token = BEARER_TOKEN_REGEX.split(bearerToken);
        if (token.length != 2 || token[1].isEmpty()) {
            throw new JwtProcessingException("Authorization header does not contain a Bearer token.");
        }
        return token[1];
    }

    private DecodedJWT verify(String token) {
        DecodedJWT unverifiedJWT;
        try {
            unverifiedJWT = JWT.decode(token);
        } catch (JWTDecodeException | IllegalArgumentException e) {
            throw new JwtProcessingException("Unable to decode token.", e);
        }

        // These fail-fast checks prevent an untrusted issuer or algorithm from triggering a JWKS fetch.
        // The same claims are required again by the cryptographic verifier below.
        if (!trustedIssuer.equals(unverifiedJWT.getIssuer())) {
            throw new JwtProcessingException("Token issuer does not match the trusted Cognito issuer.");
        }
        if (!RS256.equals(unverifiedJWT.getAlgorithm())) {
            throw new JwtProcessingException("Token must use RS256.");
        }
        List<String> audience = unverifiedJWT.getAudience();
        if (audience == null || audience.size() != 1 || !trustedAudience.equals(audience.get(0))) {
            throw new JwtProcessingException("Token audience does not match the trusted Cognito app client ID.");
        }
        Claim tokenUse = unverifiedJWT.getClaim("token_use");
        if (tokenUse == null || !ID_TOKEN_USE.equals(tokenUse.asString())) {
            throw new JwtProcessingException("Request does not contain a Cognito ID token.");
        }

        try {
            Algorithm algorithm = Algorithm.RSA256(keyProvider);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(trustedIssuer)
                    .withAudience(trustedAudience)
                    .withClaim("token_use", ID_TOKEN_USE)
                    .build();
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            LOGGER.warn("Failed to validate token against the trusted Cognito configuration.");
            throw new JwtProcessingException("Failed to validate token against the trusted Cognito configuration.", e);
        }
    }

    private static String requireConfiguration(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new JwtProcessingException(name + " must be configured.");
        }
        return value;
    }
}
