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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.amazon.aws.partners.saasfactory.services.DynamoClientService;
import com.amazon.aws.partners.saasfactory.services.S3ClientService;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

/**
 * An example of the Lambda with Cognito integration. The Authorization header containing the
 * JWT is passed into the handler, which makes calls to Cognito API to get the openID token, as well as the
 * role from the Identity Pool and the tenant - a custom cognito property.
 */
public class CognitoIdentityPoolHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoIdentityPoolHandler.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        TokenVendingMachine tokenVendingMachine = new TokenVendingMachine();
        Map<String, String> headers = requestEvent.getHeaders();
        AwsCredentialsProvider tenantCredentials = tokenVendingMachine.vendCognitoToken(headers);
        String tenant = tokenVendingMachine.getTenant();

        Region region = Region.of(System.getenv("AWS_REGION"));

        String bucket = System.getenv("S3_BUCKET");
        String table = System.getenv("DB_TABLE");
        UUID randomUUID = UUID.randomUUID();
        String json = "{\"value\": \"" + randomUUID + "\"}";
        String key = randomUUID + ".json";

        // if you used X=Tenant-Id header, we use that for S3 and DynamoDB access (but not security)
        tenant = overrideJwtTokenForTestingIfHeaderPresent(headers, tenant);

        Map<String, String> map = new HashMap<>();
        map.put("tenant", tenant);

        try {
            S3ClientService s3ClientService = new S3ClientService(bucket, tenantCredentials);
            s3ClientService.upload(json, tenant + "/" + key);

            ListObjectsV2Response listResponse = s3ClientService.listObjects(tenant);
            map.put("S3 documents visible", Integer.toString(listResponse.contents().size()));
        } catch (SdkServiceException e) {
            LOGGER.error("Issue calling the services ", e);
            map.put("S3Error", e.getMessage());
        }

        try {
            DynamoClientService dynamoClientService = new DynamoClientService(region, tenant, tenantCredentials);
            dynamoClientService.insert(table, Double.toString(Math.random()));
            QueryResponse query = dynamoClientService.query(table);
            map.put("DynamoDB rows visible", Integer.toString(query.count()));
        } catch (SdkServiceException e) {
            LOGGER.error("Issue calling the services ", e);
            map.put("DynamoDB Error", e.getMessage());
        }


        String jsonResult = "";
        try {
            jsonResult = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error serializing the JSON response ", e);
        }

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(Collections.singletonMap("timeStamp", String.valueOf(System.currentTimeMillis())))
                .withBody(jsonResult);
    }

    private String overrideJwtTokenForTestingIfHeaderPresent(Map<String, String> headers, String tenant) {
        String xTenant = headers.get("x-tenant-id");
        LOGGER.info("x-tenant-id {}", xTenant);
        if (xTenant != null && !xTenant.trim().isEmpty()) {
            return xTenant;
        }
        return tenant;
    }
}