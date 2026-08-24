package com.amazon.aws.partners.saasfactory.token;

import com.amazon.aws.partners.saasfactory.cognito.JwtClaimsExtractor;
import com.amazon.aws.partners.saasfactory.exception.JwtProcessingException;
import com.amazon.aws.partners.saasfactory.exception.PolicyAssumptionException;
import com.amazon.aws.partners.saasfactory.policy.PolicyGenerator;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JwtTokenVendorSecurityTest {

    private static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_trusted";
    private static final String AUDIENCE = "trusted-client-id";
    private static final String TENANT = "tenant-a";
    private static final String KEY_ID = "trusted-key";
    private static final String POLICY = "{\"Version\":\"2012-10-17\",\"Statement\":[]}";

    private static KeyPair trustedKeys;

    @BeforeClass
    public static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKeys = generator.generateKeyPair();
    }

    @Test
    public void trustedTokenReachesStsWithVerifiedTenant() {
        RecordingStsClient sts = new RecordingStsClient();
        JwtTokenVendor vendor = vendor(sts, token(ISSUER, AUDIENCE, "id"));

        AwsCredentialsProvider credentialsProvider = vendor.vendToken();
        AwsCredentials credentials = credentialsProvider.resolveCredentials();

        assertEquals(1, sts.calls.get());
        assertEquals(TENANT, sts.request.roleSessionName());
        assertEquals(POLICY, sts.request.policy());
        assertEquals("AKIAEXAMPLE", credentials.accessKeyId());
        assertEquals(TENANT, vendor.getTenant());
    }

    @Test
    public void accessTokenCannotReachSts() {
        RecordingStsClient sts = new RecordingStsClient();
        JwtTokenVendor vendor = vendor(sts, token(ISSUER, AUDIENCE, "access"));

        try {
            vendor.vendToken();
            fail("Expected access token to be rejected");
        } catch (PolicyAssumptionException expected) {
            assertEquals(0, sts.calls.get());
        }
    }

    @Test
    public void attackerIssuerCannotReachSts() {
        RecordingStsClient sts = new RecordingStsClient();
        JwtTokenVendor vendor = vendor(sts, token(
                "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_attacker", AUDIENCE, "id"));

        try {
            vendor.vendToken();
            fail("Expected attacker issuer to be rejected");
        } catch (PolicyAssumptionException expected) {
            assertEquals(0, sts.calls.get());
        }
    }

    private static JwtTokenVendor vendor(RecordingStsClient sts, String token) {
        RSAKeyProvider keyProvider = new RSAKeyProvider() {
            @Override
            public RSAPublicKey getPublicKeyById(String keyId) {
                if (!KEY_ID.equals(keyId)) {
                    throw new JwtProcessingException("Unknown key ID");
                }
                return (RSAPublicKey) trustedKeys.getPublic();
            }

            @Override
            public RSAPrivateKey getPrivateKey() {
                return null;
            }

            @Override
            public String getPrivateKeyId() {
                return null;
            }
        };

        return JwtTokenVendor.builder()
                .headers(Collections.singletonMap("Authorization", "Bearer " + token))
                .role("arn:aws:iam::111122223333:role/tenant-access")
                .region(Region.US_EAST_1)
                .durationSeconds(900)
                .policyGenerator(new FixedPolicyGenerator())
                .jwtClaimsExtractor(new JwtClaimsExtractor(ISSUER, AUDIENCE, keyProvider))
                .stsClient(sts)
                .build();
    }

    private static String token(String issuer, String audience, String tokenUse) {
        return JWT.create()
                .withKeyId(KEY_ID)
                .withIssuer(issuer)
                .withAudience(audience)
                .withClaim("token_use", tokenUse)
                .withClaim("custom:tenant_id", TENANT)
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000L))
                .withNotBefore(new Date(System.currentTimeMillis() - 60_000L))
                .sign(Algorithm.RSA256(
                        (RSAPublicKey) trustedKeys.getPublic(),
                        (RSAPrivateKey) trustedKeys.getPrivate()));
    }

    private static final class FixedPolicyGenerator implements PolicyGenerator {
        private String tenant;

        @Override
        public String generatePolicy() {
            return POLICY;
        }

        @Override
        public PolicyGenerator tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }

        @Override
        public String getTenant() {
            return tenant;
        }
    }

    private static final class RecordingStsClient implements StsClient {
        private final AtomicInteger calls = new AtomicInteger();
        private AssumeRoleRequest request;

        @Override
        public AssumeRoleResponse assumeRole(AssumeRoleRequest request) {
            this.request = request;
            calls.incrementAndGet();
            return AssumeRoleResponse.builder()
                    .credentials(Credentials.builder()
                            .accessKeyId("AKIAEXAMPLE")
                            .secretAccessKey("secret")
                            .sessionToken("session")
                            .build())
                    .build();
        }

        @Override
        public String serviceName() {
            return "sts";
        }

        @Override
        public void close() {
        }
    }
}
