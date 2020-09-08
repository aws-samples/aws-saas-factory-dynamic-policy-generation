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

package com.amazon.aws.partners.saasfactory.template;

import com.samskivert.mustache.Mustache;

import java.util.*;

public class PolicyTemplateProcessor {


    private final String statements;

    private final Map<String, String> data;

    public PolicyTemplateProcessor(PolicyTemplateProcessorBuilder builder) {
        this.statements = builder.statements;
        this.data = builder.data;
    }

    public static PolicyTemplateProcessorBuilder builder () {
        return new PolicyTemplateProcessorBuilder();
    }

    public String getTenantScopedPolicyTemplate() {
        String resolvedStatements = Mustache.compiler().compile(statements).execute(data);
        String policy = "{ \"Version\": \"2012-10-17\",\n  \"Statement\": [\n" + resolvedStatements + " ]\n}";
        return policy.replaceAll("\\s+", "");
    }

    public static final class PolicyTemplateProcessorBuilder {
        private String statements;
        private Map<String, String> data = new HashMap<>();

        public PolicyTemplateProcessorBuilder templates(String statements) {
            this.statements = statements;
            return this;
        }

        public PolicyTemplateProcessorBuilder data(Map<String, String> data) {
            this.data = data;
            return this;
        }

        public PolicyTemplateProcessor build() {
            return new PolicyTemplateProcessor(this);
        }
    }
}
