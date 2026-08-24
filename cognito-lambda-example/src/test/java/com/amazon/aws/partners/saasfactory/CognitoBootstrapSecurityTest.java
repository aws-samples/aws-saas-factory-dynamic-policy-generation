package com.amazon.aws.partners.saasfactory;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CognitoBootstrapSecurityTest {

    @Test
    public void identityPoolRoleIsTenantScopedEvenForDirectCredentialFlow() throws IOException {
        String template = template();
        String role = between(template, "  IdentityPoolAuthRole:\n", "  LambdaExecutionRole:\n");

        assertTrue(template.contains("Type: AWS::Cognito::IdentityPoolPrincipalTag"));
        assertTrue(template.contains("tenant_id: \"custom:tenant_id\""));
        assertTrue(role.contains("- sts:TagSession"));
        assertTrue(role.contains("${!aws:PrincipalTag/tenant_id}"));
        assertTrue(role.contains("dynamodb:LeadingKeys:"));
        assertTrue(role.contains("Null:\n                    dynamodb:LeadingKeys: false"));
        assertTrue(role.contains("${aws:PrincipalTag/tenant_id}"));

        assertFalse(role.contains("dynamodb:${AWS::Region}:${AWS::AccountId}:table/*"));
        assertFalse(role.contains("s3:::${MultiTenantS3Bucket}/*"));
        assertFalse(role.contains("sqs:"));
        assertFalse(role.contains("secretsmanager:"));
    }

    @Test
    public void everyJwtHandlerReceivesDeploymentControlledTrustConfiguration() throws IOException {
        String template = template();

        assertEquals(3, occurrences(template, "TRUSTED_COGNITO_ISSUER:"));
        assertEquals(3, occurrences(template, "TRUSTED_COGNITO_APP_CLIENT_ID:"));
        assertEquals(1, occurrences(template, "TRUSTED_COGNITO_IDENTITY_POOL_ID:"));
        assertFalse(template.contains("Name: identity_pool"));
    }

    private static String template() throws IOException {
        Path path = Paths.get("cognito-user-role-bootstrap.yml");
        if (!Files.exists(path)) {
            path = Paths.get("cognito-lambda-example", "cognito-user-role-bootstrap.yml");
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String between(String value, String start, String end) {
        int startIndex = value.indexOf(start);
        int endIndex = value.indexOf(end, startIndex);
        assertTrue("Missing expected template section", startIndex >= 0 && endIndex > startIndex);
        return value.substring(startIndex, endIndex);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
