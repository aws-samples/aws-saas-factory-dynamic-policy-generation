package com.amazon.aws.partners.saasfactory.template;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class PolicyTemplateProcessorTest {

    @Test
    public void assembleLocalPolicyTemplates() {
        Map<String, String> data = new HashMap<>();
        data.put("bucket", "mybucket");
        data.put("tenant", "123456789");
        List<String> templates = Arrays.asList("S3FolderPerTenantTemplate.json", "SecretsManagerResourceTagTemplate.json");
        String statements = PolicyTemplateLoader.assemblePolicyTemplates(templates);
        PolicyTemplateProcessor policyTemplateProcessor = PolicyTemplateProcessor.builder()
                .templates(statements)
                .data(data)
                .build();
        String assembled = policyTemplateProcessor.getTenantScopedPolicyTemplate();
        assertThat(assembled, containsString( "\"arn:aws:s3:::mybucket\""));
    }

}