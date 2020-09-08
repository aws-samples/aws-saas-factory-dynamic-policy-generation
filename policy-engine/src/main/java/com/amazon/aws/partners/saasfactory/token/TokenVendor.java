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

import com.amazon.aws.partners.saasfactory.policy.PolicyGenerator;
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
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

public class TokenVendor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenVendor.class);

    private final StsClient sts;
    private String role;
    private Region region;
    private int durationSeconds;
    private PolicyGenerator policyGenerator;

    public TokenVendor(TokenVendorBuilder builder) {
        this.durationSeconds = builder.durationSeconds;
        this.policyGenerator = builder.policyGenerator;
        this.region = builder.region;
        this.role = builder.role;

        this.sts = StsClient.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
    }

    public AwsCredentialsProvider vendToken() {
        String scopedPolicy = policyGenerator.generatePolicy();

        return getCredentialsForTenant(scopedPolicy, policyGenerator.getTenant());
    }

    private AwsCredentialsProvider getCredentialsForTenant(String scopedPolicy, String tenant) {

        StaticCredentialsProvider credentialsProvider;
        Credentials scopedCredentials;
        try {
            AssumeRoleResponse assumeRoleResponse = sts.assumeRole(assumeRoleReq -> assumeRoleReq
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

    public static TokenVendorBuilder builder() {
        return new TokenVendorBuilder();
    }

    public static class TokenVendorBuilder {
        private String role;
        private Region region;
        private int durationSeconds;
        private PolicyGenerator policyGenerator;

        public TokenVendorBuilder() {}

        public TokenVendorBuilder role(String role) {
            this.role = role;
            return this;
        }

        public TokenVendorBuilder region(Region region) {
            this.region = region;
            return this;
        }

        public TokenVendorBuilder durationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
            return this;
        }

        public TokenVendorBuilder policyGenerator(PolicyGenerator policyGenerator) {
            this.policyGenerator = policyGenerator;
            return this;
        }

        public TokenVendor build() {
            return new TokenVendor(this);
        }
    }
}
