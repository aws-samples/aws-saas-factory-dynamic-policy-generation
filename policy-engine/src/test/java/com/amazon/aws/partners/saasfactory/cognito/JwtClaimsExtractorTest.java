package com.amazon.aws.partners.saasfactory.cognito;

import com.amazon.aws.partners.saasfactory.exception.JwtProcessingException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JwtClaimsExtractorTest {

    private static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_trusted";
    private static final String ATTACKER_ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_attacker";
    private static final String AUDIENCE = "trusted-client-id";
    private static final String TENANT = "tenant-a";
    private static final String KEY_ID = "trusted-key";

    private static KeyPair trustedKeys;
    private static KeyPair attackerKeys;

    private CountingKeyProvider keyProvider;
    private JwtClaimsExtractor extractor;

    @BeforeClass
    public static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKeys = generator.generateKeyPair();
        attackerKeys = generator.generateKeyPair();
    }

    @Before
    public void setUp() {
        keyProvider = new CountingKeyProvider((RSAPublicKey) trustedKeys.getPublic());
        extractor = new JwtClaimsExtractor(ISSUER, AUDIENCE, keyProvider);
    }

    @Test
    public void trustedIdTokenReturnsVerifiedTenantClaims() {
        Map<String, Claim> claims = extractor.getClaims(headers(rsaToken(
                trustedKeys, ISSUER, AUDIENCE, "id", future(), past())));

        assertEquals(TENANT, extractor.getTenantId(claims, "custom:tenant_id"));
        assertEquals(1, keyProvider.calls.get());
    }

    @Test
    public void trustedIdentityFlowUsesConfiguredProviderAndPool() {
        String token = rsaToken(trustedKeys, ISSUER, AUDIENCE, "id", future(), past());
        CognitoClaims claims = extractor.getClaims(headers(token), "custom:tenant_id", "us-east-1:trusted-pool");

        assertEquals("us-east-1:trusted-pool", claims.getIdentityPool());
        assertEquals(Collections.singletonMap(ISSUER.substring("https://".length()), token), claims.getProviderLogins());
        assertEquals(TENANT, claims.getTenant());
    }

    @Test
    public void attackerOwnedIssuerIsRejectedBeforeJwksLookup() {
        assertRejected(rsaToken(attackerKeys, ATTACKER_ISSUER, AUDIENCE, "id", future(), past()));
        assertEquals(0, keyProvider.calls.get());
    }

    @Test
    public void wrongAudienceIsRejectedBeforeJwksLookup() {
        assertRejected(rsaToken(trustedKeys, ISSUER, "attacker-client", "id", future(), past()));
        assertEquals(0, keyProvider.calls.get());
    }

    @Test
    public void accessTokenIsRejectedBeforeJwksLookup() {
        assertRejected(rsaToken(trustedKeys, ISSUER, AUDIENCE, "access", future(), past()));
        assertEquals(0, keyProvider.calls.get());
    }

    @Test
    public void unsupportedAlgorithmIsRejectedBeforeJwksLookup() {
        String token = baseClaims(ISSUER, AUDIENCE, "id", future(), past())
                .sign(Algorithm.HMAC256("attacker-secret"));

        assertRejected(token);
        assertEquals(0, keyProvider.calls.get());
    }

    @Test
    public void tokenSignedByUntrustedKeyIsRejected() {
        assertRejected(rsaToken(attackerKeys, ISSUER, AUDIENCE, "id", future(), past()));
        assertEquals(1, keyProvider.calls.get());
    }

    @Test
    public void tamperedSignatureIsRejected() {
        String token = rsaToken(trustedKeys, ISSUER, AUDIENCE, "id", future(), past());
        String[] parts = token.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        parts[2] = replacement + parts[2].substring(1);

        assertRejected(parts[0] + "." + parts[1] + "." + parts[2]);
        assertEquals(1, keyProvider.calls.get());
    }

    @Test
    public void expiredTokenIsRejected() {
        assertRejected(rsaToken(trustedKeys, ISSUER, AUDIENCE, "id", past(), olderPast()));
        assertEquals(1, keyProvider.calls.get());
    }

    @Test
    public void notYetValidTokenIsRejected() {
        assertRejected(rsaToken(trustedKeys, ISSUER, AUDIENCE, "id", future(), future()));
        assertEquals(1, keyProvider.calls.get());
    }

    @Test
    public void policyWildcardTenantIdIsRejected() {
        String token = JWT.create()
                .withKeyId(KEY_ID)
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withClaim("token_use", "id")
                .withClaim("custom:tenant_id", "tenant-*")
                .withExpiresAt(future())
                .withNotBefore(past())
                .sign(Algorithm.RSA256(
                        (RSAPublicKey) trustedKeys.getPublic(),
                        (RSAPrivateKey) trustedKeys.getPrivate()));
        Map<String, Claim> claims = extractor.getClaims(headers(token));

        try {
            extractor.getTenantId(claims, "custom:tenant_id");
            fail("Expected unsafe tenant ID to be rejected");
        } catch (JwtProcessingException expected) {
            // expected
        }
    }

    @Test
    public void missingTrustedConfigurationFailsClosed() {
        try {
            new JwtClaimsExtractor(null, AUDIENCE, keyProvider);
            fail("Expected missing trusted issuer to be rejected");
        } catch (JwtProcessingException expected) {
            // expected
        }
    }

    private void assertRejected(String token) {
        try {
            extractor.getClaims(headers(token));
            fail("Expected token to be rejected");
        } catch (JwtProcessingException expected) {
            // expected
        }
    }

    private static Map<String, String> headers(String token) {
        return Collections.singletonMap("Authorization", "Bearer " + token);
    }

    private static String rsaToken(KeyPair keys, String issuer, String audience, String tokenUse,
                                   Date expiresAt, Date notBefore) {
        return baseClaims(issuer, audience, tokenUse, expiresAt, notBefore)
                .withKeyId(KEY_ID)
                .sign(Algorithm.RSA256((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate()));
    }

    private static JWTCreator.Builder baseClaims(String issuer, String audience, String tokenUse,
                                                  Date expiresAt, Date notBefore) {
        return JWT.create()
                .withIssuer(issuer)
                .withAudience(audience)
                .withClaim("token_use", tokenUse)
                .withClaim("custom:tenant_id", TENANT)
                .withExpiresAt(expiresAt)
                .withNotBefore(notBefore);
    }

    private static Date future() {
        return new Date(System.currentTimeMillis() + 60_000L);
    }

    private static Date past() {
        return new Date(System.currentTimeMillis() - 60_000L);
    }

    private static Date olderPast() {
        return new Date(System.currentTimeMillis() - 120_000L);
    }

    private static final class CountingKeyProvider implements RSAKeyProvider {
        private final RSAPublicKey publicKey;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingKeyProvider(RSAPublicKey publicKey) {
            this.publicKey = publicKey;
        }

        @Override
        public RSAPublicKey getPublicKeyById(String keyId) {
            calls.incrementAndGet();
            if (!KEY_ID.equals(keyId)) {
                throw new JwtProcessingException("Unknown key ID");
            }
            return publicKey;
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
}
