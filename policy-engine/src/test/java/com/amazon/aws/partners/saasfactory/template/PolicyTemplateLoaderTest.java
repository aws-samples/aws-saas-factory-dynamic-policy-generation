package com.amazon.aws.partners.saasfactory.template;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

public class PolicyTemplateLoaderTest {

    @Test
    public void retrieveTemplate() throws Exception {
        String dynamoPolicy = PolicyTemplateLoader.retrieveTemplate("DynamoLeadingKeyTemplate.json");
        assertThat(dynamoPolicy, containsString( "\"dynamodb:LeadingKeys\": [ \"{{tenant}}\" ]"));
    }

    /**
     * The condition key must be spelled exactly, with no space after the colon.
     *
     * This assertion previously required "dynamodb: LeadingKeys" -- with a space -- which
     * IAM does not recognise as a condition key. An unrecognised key is treated as absent,
     * and ForAllValues:StringEquals evaluates to TRUE when the key is absent, so the
     * tenant-scoping condition was vacuous and the statement granted dynamodb:* on the whole
     * table. It happened to work in the generator only because
     * PolicyTemplateProcessor strips all whitespace from the rendered policy, which removed
     * the space by accident; anyone reusing this template directly, which the README invites,
     * got the unscoped version. This test guards the spelling so that correctness no longer
     * depends on an unrelated minification step.
     */
    @Test
    public void conditionKeyHasNoStraySpace() throws Exception {
        String dynamoPolicy = PolicyTemplateLoader.retrieveTemplate("DynamoLeadingKeyTemplate.json");
        assertThat(dynamoPolicy, not(containsString("dynamodb: LeadingKeys")));
    }

    /**
     * ForAllValues:StringEquals passes when the condition key is absent from the request, so
     * a DynamoDB operation that supplies no LeadingKeys would satisfy it. Pairing it with a
     * Null check requires the key to be present. The same idiom is used in
     * cognito-lambda-example/cognito-user-role-bootstrap.yml and asserted by
     * CognitoBootstrapSecurityTest; this keeps the reusable template consistent with it.
     */
    @Test
    public void leadingKeysMustBePresent() throws Exception {
        String dynamoPolicy = PolicyTemplateLoader.retrieveTemplate("DynamoLeadingKeyTemplate.json");
        assertThat(dynamoPolicy, containsString("\"Null\""));
        assertThat(dynamoPolicy, containsString("\"dynamodb:LeadingKeys\": \"false\""));
    }

}