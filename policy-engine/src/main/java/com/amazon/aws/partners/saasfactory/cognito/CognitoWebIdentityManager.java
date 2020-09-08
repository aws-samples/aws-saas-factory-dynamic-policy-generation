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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient;
import software.amazon.awssdk.services.cognitoidentity.model.GetIdResponse;
import software.amazon.awssdk.services.cognitoidentity.model.GetIdentityPoolRolesResponse;
import software.amazon.awssdk.services.cognitoidentity.model.GetOpenIdTokenResponse;

import java.util.*;

public class CognitoWebIdentityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoWebIdentityManager.class);
    private final CognitoIdentityClient cognito;

    private final String identityPool;
    private final Map<String, String> providerLogins;

    public CognitoWebIdentityManager(CognitoWebIdentityManagerBuilder builder) {
        this.identityPool = builder.identityPool;
        this.providerLogins = builder.providerLogins;
        this.cognito = CognitoIdentityClient.builder()
                .region(builder.region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
    }

    public static CognitoWebIdentityManagerBuilder builder() {
        return new CognitoWebIdentityManagerBuilder();
    }

    public String getCognitoIdentityId() {
        return getCognitoIdentityId(identityPool, providerLogins);
    }

    public String getOpenIdToken() {
        return getOpenIdToken(getCognitoIdentityId(), providerLogins);
    }

    public String getIdentityPoolAuthRole() {
        return getIdentityPoolAuthRole(identityPool);
    }

    private String getCognitoIdentityId(String identityPoolId, Map<String, String> providerLogins) {
        String identityId;
        try {
            GetIdResponse getIdResponse = cognito.getId(request -> request
                    .identityPoolId(identityPoolId)
                    .logins(providerLogins)
            );
            identityId = getIdResponse.identityId();
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("CognitoWebIdentityManager::GetId", cognitoError);
            throw cognitoError;
        }
        return identityId;
    }

    private String getOpenIdToken(String identityId, Map<String, String> providerLogins) {
        String openIdToken;
        try {
            GetOpenIdTokenResponse getOpenIdTokenResponse = cognito.getOpenIdToken(getOpenIdReq -> getOpenIdReq
                    .identityId(identityId)
                    .logins(providerLogins)
            );
            openIdToken = getOpenIdTokenResponse.token();
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("CognitoWebIdentityManager::GetOpenIdToken", cognitoError);
            throw cognitoError;
        }
        return openIdToken;
    }

    private String getIdentityPoolAuthRole(String identityPoolId) {
        String role;
        try {
            GetIdentityPoolRolesResponse idPoolRolesResponse = cognito.getIdentityPoolRoles(idPoolRolesReq -> idPoolRolesReq.identityPoolId(identityPoolId));
            role = idPoolRolesResponse.roles().get("authenticated");
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("CognitoWebIdentityManager::GetIdentityPoolRoles", cognitoError);
            throw cognitoError;
        }
        return role;
    }

    public static final class CognitoWebIdentityManagerBuilder {
        private String identityPool;
        private Map<String, String> providerLogins;
        private Region region;

        private CognitoWebIdentityManagerBuilder() {
        }

        public CognitoWebIdentityManagerBuilder identityPool(String identityPool) {
            this.identityPool = identityPool;
            return this;
        }

        public CognitoWebIdentityManagerBuilder providerLogins(Map<String, String> providerLogins) {
            this.providerLogins = providerLogins;
            return this;
        }

        public CognitoWebIdentityManagerBuilder region(Region region) {
            this.region = region;
            return this;
        }

        public CognitoWebIdentityManager build() {
            return new CognitoWebIdentityManager(this);
        }
    }
}
