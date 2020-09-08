package com.amazon.aws.partners.saasfactory.template;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;

public class PolicyTemplateLoaderTest {

    @Test
    public void retrieveTemplate() throws Exception {
        String dynamoPolicy = PolicyTemplateLoader.retrieveTemplate("DynamoLeadingKeyTemplate.json");
        assertThat(dynamoPolicy, containsString( "\"dynamodb: LeadingKeys\": [ \"{{tenant}}\" ]"));
    }

}