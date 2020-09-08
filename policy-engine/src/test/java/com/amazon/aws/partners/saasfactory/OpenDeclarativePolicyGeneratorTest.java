package com.amazon.aws.partners.saasfactory;

import com.amazon.aws.partners.saasfactory.policy.OpenScopedPolicyGenerator;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;

public class OpenDeclarativePolicyGeneratorTest {

    @Test
    public void assemblePolicy() {
        Map<String, String> data = new HashMap<>();
        data.put("tenant", "A1B2C3D4");
        data.put("bucket", "TestBucket");
        List<String> templates = Arrays.asList("S3FolderPerTenantTemplate.json", "SecretsManagerResourceTagTemplate.json");

        String policy = OpenScopedPolicyGenerator.generator()
                .data(data)
                .templates(templates)
                .generatePolicy();

        assertThat(policy, containsString( "A1B2C3D4/*"));
        assertThat(policy, containsString( "arn:aws:s3:::TestBucket/A1B2C3D4/*"));
    }
}