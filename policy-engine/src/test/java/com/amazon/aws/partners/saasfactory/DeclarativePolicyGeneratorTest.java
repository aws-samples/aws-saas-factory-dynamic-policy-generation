package com.amazon.aws.partners.saasfactory;

import com.amazon.aws.partners.saasfactory.policy.DeclarativePolicyGenerator;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;

public class DeclarativePolicyGeneratorTest {

    @Test
    public void assemblePolicy() {
        String scopedPolicy = DeclarativePolicyGenerator.generator()
                .s3FolderPerTenant("TestBucket")
                .dynamoLeadingKey("employee")
                .sqsTenantQueue()
                .tenant("A1B2C3D4")
                .generatePolicy();

        System.out.println(scopedPolicy);
        assertThat(scopedPolicy, containsString( "A1B2C3D4/*"));
        assertThat(scopedPolicy, containsString( "arn:aws:s3:::TestBucket/A1B2C3D4/*"));
        assertThat(scopedPolicy, containsString( "\"dynamodb:LeadingKeys\":[\"A1B2C3D4\"]"));
        assertThat(scopedPolicy, containsString( "\"arn:aws:sqs:*:*:A1B2C3D4_queue\""));
    }
}