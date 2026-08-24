package com.amazon.aws.partners.saasfactory.cognito;

import com.amazon.aws.partners.saasfactory.exception.JwtProcessingException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.interfaces.RSAKeyProvider;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.regex.Pattern;

public class CognitoRSAKeyProvider implements RSAKeyProvider {

    private static final Pattern COGNITO_HOST = Pattern.compile(
            "^cognito-idp\\.[a-z0-9-]+\\.amazonaws\\.com(?:\\.cn)?$");

    private final URL jwksUrl;

    public CognitoRSAKeyProvider(String trustedIssuer) {
        try {
            URL issuer = new URL(trustedIssuer);
            String path = issuer.getPath();
            if (!"https".equals(issuer.getProtocol())
                    || issuer.getPort() != -1
                    || issuer.getUserInfo() != null
                    || issuer.getQuery() != null
                    || issuer.getRef() != null
                    || !COGNITO_HOST.matcher(issuer.getHost()).matches()
                    || path == null
                    || path.length() <= 1
                    || path.substring(1).contains("/")) {
                throw new JwtProcessingException("Trusted issuer must be an HTTPS Cognito user-pool issuer.");
            }
            this.jwksUrl = new URL(trustedIssuer + "/.well-known/jwks.json");
        } catch (MalformedURLException e) {
            throw new JwtProcessingException("Unable to generate trusted Cognito issuer URL.", e);
        }
    }

    @Override
    public RSAPublicKey getPublicKeyById(String keyId) {
        try {
            JwkProvider provider = new JwkProviderBuilder(jwksUrl).build();
            Jwk jwk = provider.get(keyId);
            return (RSAPublicKey) jwk.getPublicKey();
        } catch (Exception e) {
            throw new JwtProcessingException("Failed to retrieve a key from the trusted Cognito issuer.", e);
        }
    }

    @Override
    public RSAPrivateKey getPrivateKey() {
        return null;
    }

    @Override
    public String getPrivateKeyId() {
        return null;
    }
}
