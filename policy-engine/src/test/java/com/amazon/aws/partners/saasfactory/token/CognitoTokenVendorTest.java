package com.amazon.aws.partners.saasfactory.token;

import com.amazon.aws.partners.saasfactory.exception.PolicyAssumptionException;
import org.junit.Test;
import software.amazon.awssdk.regions.Region;

public class CognitoTokenVendorTest {

    @Test(expected = PolicyAssumptionException.class)
    public void getCredentialsForTenant_empty() {
        CognitoTokenVendor vendor = CognitoTokenVendor.builder()
                .region(Region.US_EAST_1)
                .trustedIssuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_trusted")
                .trustedAudience("trusted-client-id")
                .trustedIdentityPool("us-east-1:trusted-pool")
                .build();

        vendor.getCredentialsForTenant("", "", "", "");
    }
}
