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
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class JwtClaimsExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtClaimsExtractor.class);

    private static final Pattern BEARER_TOKEN_REGEX = Pattern.compile("^[B|b]earer +");

    public JwtClaimsExtractor() { }

    public Map<String, Claim> getClaims(Map<String, String> request) {
        String bearerToken = getBearerToken(request);
        DecodedJWT unverifiedJWT = JWT.decode(bearerToken);
        String issuer = unverifiedJWT.getIssuer();
        DecodedJWT verifiedJWT = verify(bearerToken, issuer);
        return verifiedJWT.getClaims();
    }

    public CognitoClaims getClaims(Map<String, String> request, String tenantClaim, String identityPoolClaim) {
        String bearerToken = getBearerToken(request);
        DecodedJWT unverifiedJWT = JWT.decode(bearerToken);
        String issuer = unverifiedJWT.getIssuer();
        DecodedJWT verifiedJWT = verify(bearerToken, issuer);
        Map<String, Claim> claims = verifiedJWT.getClaims();
        Map<String, String> providerLogins = getProviderLogins(claims, bearerToken, issuer);
        String tenantId = getTenantId(claims, tenantClaim);
        String identityPoolId = getIdentityPoolId(claims, identityPoolClaim);
        return CognitoClaims.builder()
                .identityPool(identityPoolId)
                .providerLogins(providerLogins)
                .tenant(tenantId)
                .build();
    }

    public String getTenantId(Map<String, Claim> claims, String claimName) {
        String tenantId = null;
        Claim tenantClaim = claims.get(claimName);
        if (tenantClaim != null) {
            tenantId = tenantClaim.asString();
        }
        if (tenantId == null) {
            throw new JwtProcessingException("No tenant id in token");
        }
        return tenantId;
    }

    public String getIdentityPoolId(Map<String, Claim> claims, String claimName) {
        String identityPoolId = null;
        Claim identityPoolClaim = claims.get(claimName);
        if (identityPoolClaim != null) {
            identityPoolId = identityPoolClaim.asString();
        }
        if (identityPoolId == null || identityPoolId.isEmpty()) {
            throw new JwtProcessingException("No Cognito Identity Pool ID in token");
        }
        return identityPoolId;
    }

    public Map<String, String> getProviderLogins(Map<String, Claim> claims , String bearerToken, String issuer) {
        Map<String, String> logins = new HashMap<>();
        String provider = issuer.replace("https://", "");
        checkIdToken(claims);
        logins.put(provider, bearerToken);
        return logins;
    }

    private void checkIdToken(Map<String, Claim> claims) {
        Claim tokenUseClaim = claims.get("token_use");
        if (!"id".equals(tokenUseClaim.asString())) {
            throw new JwtProcessingException("Request does not contain an ID Token");
        }
    }

    private String getBearerToken(Map<String, String> request) {
        String jwt = null;
        if (request != null) {
            String bearerToken = null;
            if (request.containsKey("Authorization")) {
                bearerToken = request.get("Authorization");
            } else if (request.containsKey("authorization")) {
                bearerToken = request.get("authorization");
            } else {
                LOGGER.error("Request does not contain an Authorization header");
            }
            if (bearerToken != null) {
                String[] token = BEARER_TOKEN_REGEX.split(bearerToken);
                if (token.length == 2 && !token[1].isEmpty()) {
                    jwt = token[1];
                } else {
                    LOGGER.error("Authorization header does not contain Bearer token");
                }
            } else {
                LOGGER.error("Request does not contain Authorization header");
            }
        }
        return jwt;
    }

    private DecodedJWT verify(String token, String issuer) {
        try {
            RSAKeyProvider keyProvider = new CognitoRSAKeyProvider(issuer);
            Algorithm algorithm = Algorithm.RSA256(keyProvider);
            JWTVerifier verifier = JWT.require(algorithm)
                    .build();
            return verifier.verify(token);
        } catch (JWTVerificationException e){
            LOGGER.error("Failed to validate token with issuer.", e);
            throw new JwtProcessingException("Failed to validate token with issuer.");
        }
    }

}
