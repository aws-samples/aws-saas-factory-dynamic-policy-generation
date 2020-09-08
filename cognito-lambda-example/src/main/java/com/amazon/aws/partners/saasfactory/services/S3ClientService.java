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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.util.HashMap;
import java.util.Map;

/**
 * An example client for interacting with the S3 service.
 */
public class S3ClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3ClientService.class);

    private final String bucket;

    private final S3Client s3Client;

    public S3ClientService(String bucket, AwsCredentialsProvider tenantCredentials) {
        this.bucket = bucket;
        s3Client = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(tenantCredentials)
                .build();
    }

    public void upload(String json, String key) {
        // S3 stores user-defined metadata keys in lowercase
        Map<String, String> metadata = new HashMap<>();
        metadata.put("", "important stuff");

        // GetObject would be implemented similarly
        LOGGER.info("S3ClientService::upload putting payload {} to s3://{}}/{}", json, bucket, key);
        s3Client.putObject(r -> r
                        .bucket(bucket)
                        .key(key)
                        .contentType("text/json")
                        .metadata(metadata)
                , RequestBody.fromString(json)
        );
    }

    public ListObjectsV2Response listObjects(String tenant) {
        LOGGER.info("S3ClientService::listObjects listing bucket {}/{}", bucket, tenant);
        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listReq -> listReq
                .bucket(bucket)
                .prefix(tenant)
        );
        LOGGER.info("Bucket has {} objects", (listResponse.contents().size() - 1));
        return listResponse;
    }

}
