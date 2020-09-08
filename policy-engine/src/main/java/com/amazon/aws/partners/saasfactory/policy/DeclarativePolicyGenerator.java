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

package com.amazon.aws.partners.saasfactory.policy;

import com.amazon.aws.partners.saasfactory.exception.PolicyAssumptionException;
import com.amazon.aws.partners.saasfactory.template.PolicyTemplateLoader;
import com.amazon.aws.partners.saasfactory.template.PolicyTemplateProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Version of the Policy Generator which provides more structure around the templates. While this ensures that the
 * proper variables are set for each template, your developers can only use pre-defined templates.
 */
public class DeclarativePolicyGenerator implements PolicyGenerator {

    private final List<String> templates = new ArrayList<>();

    private final Map<String, String> data = new HashMap<>();

    public static DeclarativePolicyGenerator generator() {
        return new DeclarativePolicyGenerator();
    }

    public String getTenant() {
        return data.get("tenant");
    }

    public DeclarativePolicyGenerator tenant(String tenant) {
        data.put("tenant", tenant);
        return this;
    }

    public DeclarativePolicyGenerator s3FolderPerTenant(String bucket) {
        templates.add("S3FolderPerTenantTemplate.json");
        data.put("bucket", bucket);
        return this;
    }

    public DeclarativePolicyGenerator dynamoLeadingKey(String table) {
        templates.add("DynamoLeadingKeyTemplate.json");
        data.put("table", table);
        return this;
    }

    public DeclarativePolicyGenerator sqsTenantQueue() {
        templates.add("SQSTenantQueueTemplate.json");
        return this;
    }

    public DeclarativePolicyGenerator secretsManagerResourceTag() {
        templates.add("SecretsManagerResourceTagTemplate.json");
        return this;
    }

    public DeclarativePolicyGenerator efSAccessPointPerTenant() {
        templates.add("EFSAccessPointPerTenantTemplate.json");
        return this;
    }

    public DeclarativePolicyGenerator parameterStorePathPerTenant(String tenantPath) {
        templates.add("ParameterStorePathPerTenantTemplate.json");
        data.put("tenant_path", tenantPath);
        return this;
    }

    public String generatePolicy() {
        if(templates.isEmpty()) {
            throw new PolicyAssumptionException("A scoped policy must contain at least one statement");
        }
        String tenant = getTenant();
        if(tenant == null || tenant.trim().equals("")) {
            throw new PolicyAssumptionException("A scoped policy must contain a tenant.");
        }
        String statements = PolicyTemplateLoader.assemblePolicyTemplates(templates);
        PolicyTemplateProcessor policyTemplateProcessor = PolicyTemplateProcessor.builder()
                .data(data)
                .templates(statements)
                .build();
        return policyTemplateProcessor.getTenantScopedPolicyTemplate();
    }

}
