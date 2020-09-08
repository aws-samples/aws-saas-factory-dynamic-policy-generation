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

import com.amazon.aws.partners.saasfactory.policy.PolicyGenerator;
import com.amazon.aws.partners.saasfactory.policy.DeclarativePolicyGenerator;
import com.amazon.aws.partners.saasfactory.service.*;
import com.amazon.aws.partners.saasfactory.token.TokenVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.efs.model.EfsException;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.services.ssm.model.SsmException;

import java.util.UUID;

public class CommandLineTokenVendorExample {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandLineTokenVendorExample.class);

    private Region region;

    public static void main(String[] args) {
        String accountId = args[0];
        Region region = Region.of(args[1]);
        CommandLineTokenVendorExample example = new CommandLineTokenVendorExample();
        example.execute(accountId, region);
    }
    
    public void execute(String accountId, Region region) {
        this.region = region;
        String role = "arn:aws:iam::" + accountId + ":role/role-for-assuming-policies";

        // Tenant we are allowing access
        String myTenant = "123456789";

        // Second tenant, use to show access denied to first tenants resources
        String otherTenant = "987654321";

        // S3 Variable
        String bucket = "your-bucket-name";

        // Dynamo DB Variable
        String table = "table-name";

        // EFS Variable
        String accessPoint = "fsap-yyyyyyyyyyyy";
        String inaccessibleAccessPoint = "fsap-xxxxxxxxxxx";

        // Secrets Manager Variable
        String secret = "secret";
        String otherSecret = "other-tenant";

        // Parameter Store Variable
        String path = "your-path/";

        PolicyGenerator policyGenerator = DeclarativePolicyGenerator.generator()
                .s3FolderPerTenant(bucket)
                //.dynamoLeadingKey(table)
                //.sqsTenantQueue()
                //.secretsManagerResourceTag()
                //.efSAccessPointPerTenant()
                //.parameterStorePathPerTenant(path + myTenant)
                .tenant(myTenant);

        TokenVendor tokenVendor = TokenVendor.builder()
                .policyGenerator(policyGenerator)
                .durationSeconds(900)
                .region(region)
                .role(role)
                .build();

        AwsCredentialsProvider tenantCredentials = tokenVendor.vendToken();
        
        // S3
        String json = "{\"value\": \"test\"}";
        useCredentialsForS3(tenantCredentials, json, bucket,  myTenant + "/" + UUID.randomUUID() + ".json", myTenant);
        // proof that you can't upload to your bucket outside your folder
        useCredentialsForS3(tenantCredentials, json, bucket,  UUID.randomUUID() + ".json", myTenant);


        //Dynamo
        /*useCredentialsForDynamo(tenantCredentials, table, myTenant);
        useCredentialsForDynamo(tenantCredentials, table, otherTenant);*/

        //SQS
        /*String queueUrl = "https://sqs." + region + ".amazonaws.com/" + accountId + "/" + myTenant + "_queue";
        String inaccessibleQueueUrl = "https://sqs." + region + ".amazonaws.com/" + accountId + "/" + otherTenant + "_queue";
        useCredentialsForSqs(tenantCredentials, json, queueUrl);
        useCredentialsForSqs(tenantCredentials, json, inaccessibleQueueUrl);*/

        // Secrets Manager
        /*useCredentialsForSecretsManager(tenantCredentials, secret);
        useCredentialsForSecretsManager(tenantCredentials, otherSecret);*/

        // EFS
        /*useCredentialsForEfs(tenantCredentials, accessPoint);
        useCredentialsForEfs(tenantCredentials, inaccessibleAccessPoint);*/

        // SSM Parameter Store
        /*useCredentialsForParameterStore(tenantCredentials, "/" + path + myTenant);
        useCredentialsForParameterStore(tenantCredentials, "/" + path + otherTenant);*/

    }

    public void useCredentialsForS3(AwsCredentialsProvider tenantCredentials, String json, String bucket, String fileName, String tenant) {
        S3ClientService s3ClientService = new S3ClientService(bucket, tenantCredentials);
        try {
            s3ClientService.listObjects(tenant);
            s3ClientService.upload(json, fileName);
        } catch (SdkServiceException s3error) {
            LOGGER.info("Prevented from access outside of the tenant folder.");
        }
    }

    public void useCredentialsForDynamo(AwsCredentialsProvider tenantCredentials, String table, String currentTenant) {
        DynamoClientService dynamoClientService = new DynamoClientService(region, currentTenant, tenantCredentials);
        try {
            dynamoClientService.insert(table, UUID.randomUUID().toString());
            dynamoClientService.query(table);
        } catch (DynamoDbException e) {
            LOGGER.error("Prevented from accessing table with a leading key of a different tenant");
        }
    }

    public void useCredentialsForSqs(AwsCredentialsProvider tenantCredentials, String json, String queueUrl) {
        SqsClientService sqsClientService = new SqsClientService(region, tenantCredentials);
        try {
            sqsClientService.sendMessage(json, queueUrl);
        } catch (SqsException e) {
            LOGGER.info("Prevented from sending a message to a queue not in their policy.");
        }
    }

    public void useCredentialsForSecretsManager(AwsCredentialsProvider tenantCredentials, String secretName) {
        SecretsManagerClientService secretsManagerClientService = new SecretsManagerClientService(region, tenantCredentials);
        secretsManagerClientService.getSecretString(secretName);
    }

    public void useCredentialsForParameterStore(AwsCredentialsProvider tenantCredentials, String parameterName) {
        ParameterStoreClientService parameterStoreClientService = new ParameterStoreClientService(region, tenantCredentials);
        try {
            parameterStoreClientService.getParameterString(parameterName);
        } catch (SsmException e) {
            LOGGER.info("Prevented from retrieving a parameter not in their policy.");
        }
    }

    public void useCredentialsForEfs(AwsCredentialsProvider tenantCredentials, String accessPoint) {
        EFSClientService efsClientService = new EFSClientService(region, tenantCredentials);
        try {
            efsClientService.describeAccessPoint(accessPoint);
        } catch (EfsException e) {
            LOGGER.info("Prevented from describing access point not in their policy.");
        }
    }

}
