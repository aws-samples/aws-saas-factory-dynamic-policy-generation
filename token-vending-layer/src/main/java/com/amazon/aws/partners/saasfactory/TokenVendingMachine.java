
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

package com.amazon.aws.partners.saasfactory;

import java.util.Map;

import com.amazon.aws.partners.saasfactory.policy.PolicyGenerator;
import com.amazon.aws.partners.saasfactory.policy.DeclarativePolicyGenerator;
import com.amazon.aws.partners.saasfactory.token.CognitoTokenVendor;
import com.amazon.aws.partners.saasfactory.token.JwtTokenVendor;
import com.amazon.aws.partners.saasfactory.token.TokenVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * All of these examples are coded to just grant permissions to S3 and Dynamo DB. Your implementation needs to handle
 * the different permutations of policy permissions your calling services require.
 */
public class TokenVendingMachine {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenVendingMachine.class);

    private String tenant;

    private static final String AWS_REGION = "AWS_REGION";
    private static final String S3_BUCKET = "S3_BUCKET";
    private static final String DB_TABLE = "DB_TABLE";

    /**
     * Validates the JWT token with Cognito (our IdP in this example) in code using their Json Web Key Sets (JWKS),
     * extracts the tenant and identity provider from the JWT claims
     * calls Cognito Identity Provider to retrieve the IAM role
     * and uses the tenant and role to vend the token.
     *
     * @param headers the HTTP headers which contain an authorization header.
     * @return the scoped credentials
     */
    public AwsCredentialsProvider vendCognitoToken(Map<String, String> headers) {

        Region region = Region.of(System.getenv(AWS_REGION));

        String bucket = System.getenv(S3_BUCKET);
        String table = System.getenv(DB_TABLE);

        PolicyGenerator policyGenerator = DeclarativePolicyGenerator.generator()
                .dynamoLeadingKey(table)
                .s3FolderPerTenant(bucket);

        CognitoTokenVendor cognitoTokenVendor = CognitoTokenVendor.builder()
                .policyGenerator(policyGenerator)
                .durationSeconds(900)
                .headers(headers)
                .region(region)
                .build();

        AwsCredentialsProvider awsCredentialsProvider = cognitoTokenVendor.vendToken();
        this.tenant = cognitoTokenVendor.getTenant();

        LOGGER.info("Vending Cognito security token for tenant {}", tenant);

        return awsCredentialsProvider;
    }

    /**
     * Validates the JWT token with Cognito (our IdP in this example) in code using their Json Web Key Sets (JWKS),
     * extracts the tenant from the JWT claims
     * and uses the tenant to vend the token.
     *
     * @param headers the HTTP headers which contain an authorization header.
     * @param role    the role to assume.
     * @return the scoped credentials
     */
    public AwsCredentialsProvider vendToken(Map<String, String> headers, String role) {

        Region region = Region.of(System.getenv(AWS_REGION));

        String bucket = System.getenv(S3_BUCKET);
        String table = System.getenv(DB_TABLE);

        PolicyGenerator policyGenerator = DeclarativePolicyGenerator.generator()
                .dynamoLeadingKey(table)
                .s3FolderPerTenant(bucket);

        JwtTokenVendor jwtTokenVendor = JwtTokenVendor.builder()
                .policyGenerator(policyGenerator)
                .durationSeconds(900)
                .headers(headers)
                .role(role)
                .region(region)
                .build();

        AwsCredentialsProvider awsCredentialsProvider = jwtTokenVendor.vendToken();
        tenant = jwtTokenVendor.getTenant();

        LOGGER.info("Vending JWT security token for tenant {}", tenant);

        return awsCredentialsProvider;
    }

    /**
     * Does not validate the JWT token with an IdP
     * extracts the tenant from the JWT claims
     * and uses the tenant to vend the token.
     *
     * @param headers the HTTP headers which contain an authorization header.
     * @param role    the role to assume.
     * @return the scoped credentials
     */
    public AwsCredentialsProvider vendTokenNoJwtValidation(Map<String, String> headers, String role) {

        Region region = Region.of(System.getenv(AWS_REGION));

        String bucket = System.getenv(S3_BUCKET);
        String table = System.getenv(DB_TABLE);

        PolicyGenerator policyGenerator = DeclarativePolicyGenerator.generator()
                .dynamoLeadingKey(table)
                .s3FolderPerTenant(bucket);

        JwtTokenVendor jwtTokenVendor = JwtTokenVendor.builder()
                .policyGenerator(policyGenerator)
                .durationSeconds(900)
                .headers(headers)
                .role(role)
                .region(region)
                .validateToken(false)
                .build();

        AwsCredentialsProvider awsCredentialsProvider = jwtTokenVendor.vendToken();
        tenant = jwtTokenVendor.getTenant();

        LOGGER.info("Vending JWT security token for tenant {}", tenant);

        return awsCredentialsProvider;
    }

    /**
     * Expect the tenant to already be in the authorizer claims, since the API Gateway will have processed the JWT.
     * uses the tenant to vend the token.
     *
     * @param authorizer the HTTP headers which contain an authorization header.
     * @param role    the role to assume.
     * @return the scoped credentials
     */
    public AwsCredentialsProvider vendTokenAuthorizer(Map<String, Object> authorizer, String role) {

        Region region = Region.of(System.getenv(AWS_REGION));

        String bucket = System.getenv(S3_BUCKET);
        String table = System.getenv(DB_TABLE);

        Map<String, Object> claims = (Map<String, Object>) authorizer.get("claims");
        tenant = (String) claims.get("custom:tenant_id");

        PolicyGenerator policyGenerator = DeclarativePolicyGenerator.generator()
                .dynamoLeadingKey(table)
                .s3FolderPerTenant(bucket)
                .tenant(tenant);

        TokenVendor stsTokenVendor = TokenVendor.builder()
                .policyGenerator(policyGenerator)
                .durationSeconds(900)
                .role(role)
                .region(region)
                .build();

        AwsCredentialsProvider awsCredentialsProvider = stsTokenVendor.vendToken();

        LOGGER.info("Vending security token for tenant {}", tenant);

        return awsCredentialsProvider;
    }

    public String getTenant() {
        return tenant;
    }

}
