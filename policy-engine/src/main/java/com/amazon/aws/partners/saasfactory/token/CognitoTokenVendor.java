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

package com.amazon.aws.partners.saasfactory.token;

import com.amazon.aws.partners.saasfactory.cognito.CognitoClaims;
import com.amazon.aws.partners.saasfactory.cognito.CognitoWebIdentityManager;
import com.amazon.aws.partners.saasfactory.cognito.JwtClaimsExtractor;
import com.amazon.aws.partners.saasfactory.exception.PolicyAssumptionException;
import com.amazon.aws.partners.saasfactory.policy.PolicyGenerator;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.util.Map;

public class CognitoTokenVendor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoTokenVendor.class);

    private static final String TENANT_CLAIM = "custom:tenant_id";
    private static final String COGNITO_IDENTITY_POOL_CLAIM = "custom:identity_pool";

    private final StsClient sts;
    private String tenant;
    private final Region region;
    private final int durationSeconds;
    private final PolicyGenerator policyGenerator;
    private final Map<String, String> headers;

    public CognitoTokenVendor(CognitoTokenVendorBuilder builder) {
        this.durationSeconds = builder.durationSeconds;
        this.policyGenerator = builder.policyGenerator;
        this.region = builder.region;
        this.headers = builder.headers;

        this.sts = StsClient.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
    }

    public AwsCredentialsProvider vendToken() {

        JwtClaimsExtractor jwtClaimsExtractor = new JwtClaimsExtractor();
        CognitoClaims cognitoClaims = jwtClaimsExtractor.getClaims(headers, TENANT_CLAIM, COGNITO_IDENTITY_POOL_CLAIM);

        String identityPool;
        Map<String, String> providerLogins;

        try {
            identityPool = cognitoClaims.getIdentityPool();
            providerLogins = cognitoClaims.getProviderLogins();
            this.tenant = cognitoClaims.getTenant();
            LOGGER.info("Injecting tenant {} from JWT.", tenant);
        } catch (ExpiredJwtException e) {
            LOGGER.info("Using an expired JWT Token.", e);
            throw new PolicyAssumptionException("Unable to verify your identity.");
        }

        LOGGER.info("Injecting tenant {} from JWT.", tenant);

        CognitoWebIdentityManager cognitoWebIdentityManager = CognitoWebIdentityManager.builder()
                .region(region)
                .identityPool(identityPool)
                .providerLogins(providerLogins)
                .build();

        String openIdToken = cognitoWebIdentityManager.getOpenIdToken();
        String role = cognitoWebIdentityManager.getIdentityPoolAuthRole();

        policyGenerator.tenant(this.tenant);
        String scopedPolicy = policyGenerator.generatePolicy();

        return getCredentialsForTenant(scopedPolicy, role, tenant, openIdToken);
    }

    AwsCredentialsProvider getCredentialsForTenant(String scopedPolicy,
                                                           String role,
                                                           String tenant,
                                                           String openIdToken) {

        StaticCredentialsProvider credentialsProvider;
        Credentials scopedCredentials;
        if(scopedPolicy == null || scopedPolicy.trim().isEmpty()) {
            LOGGER.info("CognitoTokenVendor::Attempting to assumeRole with empty policy, should not happen!");
            throw new PolicyAssumptionException("Missing or empty policy, cannot allow access.");
        }
        try {
            AssumeRoleWithWebIdentityResponse assumeRoleResponse = sts.assumeRoleWithWebIdentity(assumeRoleReq -> assumeRoleReq
                    .webIdentityToken(openIdToken)
                    .durationSeconds(durationSeconds)
                    .policy(scopedPolicy)
                    .roleArn(role)
                    .roleSessionName(tenant)
            );

            scopedCredentials = assumeRoleResponse.credentials();
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(scopedCredentials.accessKeyId(), scopedCredentials.secretAccessKey(), scopedCredentials.sessionToken())
            );
        } catch (SdkServiceException stsError) {
            LOGGER.error("STS::AssumeRole", stsError);
            throw stsError;
        }

        return credentialsProvider;
    }

    public static CognitoTokenVendorBuilder builder() {
        return new CognitoTokenVendorBuilder();
    }

    public String getTenant() {
        return tenant;
    }

    public static final class CognitoTokenVendorBuilder {
        private Region region;
        private int durationSeconds;
        private PolicyGenerator policyGenerator;
        private Map<String, String> headers;

        private CognitoTokenVendorBuilder() {
        }

        public CognitoTokenVendorBuilder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public CognitoTokenVendorBuilder region(Region region) {
            this.region = region;
            return this;
        }

        public CognitoTokenVendorBuilder durationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
            return this;
        }

        public CognitoTokenVendorBuilder policyGenerator(PolicyGenerator policyGenerator) {
            this.policyGenerator = policyGenerator;
            return this;
        }

        public CognitoTokenVendor build() {
            return new CognitoTokenVendor(this);
        }
    }

}
