package com.amazon.aws.partners.saasfactory.cognito;

import java.util.Map;

public class CognitoClaims {
    private String identityPool;
    private Map<String, String> providerLogins;
    private String tenant;

    public static CognitoClaimsBuilder builder() {
        return new CognitoClaimsBuilder();
    }

    public CognitoClaims(CognitoClaimsBuilder builder) {
        this.identityPool = builder.identityPool;
        this.providerLogins = builder.providerLogins;
        this.tenant = builder.tenant;
    }

    public String getIdentityPool() {
        return identityPool;
    }

    public void setIdentityPool(String identityPool) {
        this.identityPool = identityPool;
    }

    public Map<String, String> getProviderLogins() {
        return providerLogins;
    }

    public String getTenant() {
        return tenant;
    }


    public static final class CognitoClaimsBuilder {
        private String identityPool;
        private Map<String, String> providerLogins;
        private String tenant;

        private CognitoClaimsBuilder() {
        }

        public CognitoClaims build() {
            return new CognitoClaims(this);
        }

        public CognitoClaimsBuilder identityPool(String identityPool) {
            this.identityPool = identityPool;
            return this;
        }

        public CognitoClaimsBuilder providerLogins(Map<String, String> providerLogins) {
            this.providerLogins = providerLogins;
            return this;
        }

        public CognitoClaimsBuilder tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }
    }

}
