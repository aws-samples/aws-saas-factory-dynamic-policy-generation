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

public class CognitoRSAKeyProvider implements RSAKeyProvider {

    private final URL issuer;

    public CognitoRSAKeyProvider(String issuer) {
        try {
            this.issuer = new URL(issuer + "/.well-known/jwks.json");
        } catch (MalformedURLException e) {
            throw new JwtProcessingException("Unable to generate issuer URL.");
        }
    }

    @Override
    public RSAPublicKey getPublicKeyById(String keyId) {
        try {
            JwkProvider provider = new JwkProviderBuilder(issuer).build();
            Jwk jwk = provider.get(keyId);
            return (RSAPublicKey) jwk.getPublicKey();
        } catch (Exception e) {
            throw new JwtProcessingException("Failed to get validate token with issuer.", e);
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