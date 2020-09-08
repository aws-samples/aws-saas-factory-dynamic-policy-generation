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
package com.amazon.aws.partners.saasfactory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.efs.EfsClient;
import software.amazon.awssdk.services.efs.model.DescribeAccessPointsRequest;
import software.amazon.awssdk.services.efs.model.DescribeAccessPointsResponse;

/**
 * An example of connecting to access points restricted by the EFSAccessPointPerTenantTemplate policy.
 */
public class EFSClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EFSClientService.class);

    private final EfsClient efsClient;

    public EFSClientService(Region region, AwsCredentialsProvider tenantCredentials) {
        efsClient = EfsClient.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(tenantCredentials)
                .build();
    }

    public void describeAccessPoint(String accessPoint) {

        DescribeAccessPointsRequest describeAccessPointsRequest = DescribeAccessPointsRequest.builder()
                .accessPointId(accessPoint)
                .build();
        DescribeAccessPointsResponse describeAccessPointsResponse = efsClient.describeAccessPoints
                (describeAccessPointsRequest);

        LOGGER.info("Successfully accessed the access point {}} = ", describeAccessPointsResponse);

    }

}
