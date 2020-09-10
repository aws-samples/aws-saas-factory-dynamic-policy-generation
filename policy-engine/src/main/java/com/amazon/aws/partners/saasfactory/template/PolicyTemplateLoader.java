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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class PolicyTemplateLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyTemplateLoader.class);

    public static String retrieveTemplate(final String policyTemplate) throws IOException {
        InputStream inputStream = PolicyTemplateLoader.class.getResourceAsStream(policyTemplate);
        if (inputStream == null) {
            inputStream = PolicyTemplateLoader.class.getClassLoader().getResourceAsStream(policyTemplate);
        }
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader reader = new BufferedReader(inputStreamReader);
        StringBuilder sb = new StringBuilder();
        String str;
        while((str = reader.readLine())!= null){
            sb.append(str).append("\n");
        }
        return sb.toString();
    }

    public static String assemblePolicyTemplates(List<String> templates) {
        List<String> statements = new ArrayList<>();

        for (String template : templates) {
            String policy;
            try {
                policy = retrieveTemplate(template);
            } catch (IOException e) {
                LOGGER.info("Unable to locate template for {} ", template);
                throw new RuntimeException("Unable to locate template for " + template);
            }
            statements.add(policy);
        }
        return String.join(",", statements);
    }

}
