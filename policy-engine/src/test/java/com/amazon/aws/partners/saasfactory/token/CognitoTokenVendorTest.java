package com.amazon.aws.partners.saasfactory.token;

import com.amazon.aws.partners.saasfactory.exception.PolicyAssumptionException;
import org.junit.Test;

public class CognitoTokenVendorTest {

    @Test(expected = PolicyAssumptionException.class)
    public void getCredentialsForTenant_empty() {
        CognitoTokenVendor vendor = CognitoTokenVendor.builder()
                .build();

        vendor.getCredentialsForTenant("", "","", "");
    }

}