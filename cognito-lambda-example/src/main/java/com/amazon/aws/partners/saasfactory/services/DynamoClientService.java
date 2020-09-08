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
package com.amazon.aws.partners.saasfactory.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;

/**
 * An example client for interacting with DynamoDB.
 */
public class DynamoClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoClientService.class);

    private final String tenant;

    private final DynamoDbClient dynamoDbClient;

    public DynamoClientService(Region region, String tenant, AwsCredentialsProvider tenantCredentials) {
        this.tenant = tenant;
        dynamoDbClient = DynamoDbClient.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(tenantCredentials)
                .build();

    }

    public void insert(String tableName, String nameValue) {
        HashMap<String, AttributeValue> itemValues = new HashMap<>();
        itemValues.put("tenant", AttributeValue.builder().s(tenant).build());
        itemValues.put("name", AttributeValue.builder().s(nameValue).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(tableName)
                .item(itemValues)
                .build();

        dynamoDbClient.putItem(putItemRequest);
    }

    public QueryResponse query(String tableName) {
        String partitionKeyName = "tenant";
        String partitionAlias = "#a";

        //set up an alias for the partition key name in case it's a reserved word
        HashMap<String, String> attrNameAlias = new HashMap<>();

        attrNameAlias.put(partitionAlias, partitionKeyName);

        //set up mapping of the partition name with the value
        HashMap<String, AttributeValue> attrValues = new HashMap<>();
        attrValues.put(":" + partitionKeyName, AttributeValue.builder().s(tenant).build());

        // Create a QueryRequest object
        QueryRequest queryReq = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression(partitionAlias + " = :" + partitionKeyName)
                .expressionAttributeNames(attrNameAlias)
                .expressionAttributeValues(attrValues)
                .build();

        QueryResponse response = dynamoDbClient.query(queryReq);
        LOGGER.info("Records matching the tenant {}} = {}", tenant, response.count());
        return response;
    }

}
