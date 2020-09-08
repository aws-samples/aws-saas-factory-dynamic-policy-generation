# Command Line Example

A simple Java command line program that demonstrates the Token Vending Machine using local AWS credentials. 
This example is simplistic, and not how a real world system would function. There is no authentication mechanism
here, the tenant is hard coded.

This code is useful for quickly testing out templates and proving they enforce tenant isolation.

## Setup

### CloudFormation

A template **user-role-bootstrap.yml** is included which creates a demo user "assuming-user-1" 
and role. "role-for-assuming-policies" 
The role includes all the permissions needed to use any of the permissions templates included in this project. 

<p><kbd><img width=1015 height=492 src="../images/Cognito CF Variables.png" alt="Cognito CloudFormation Variables"/></kbd></p>

### Setup IAM User Permissions

You will need to setup and access key for the user "assuming-user-1" we created above.

[IAM Access Key Instructions](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html#Using_CreateAccessKey_CLIAPI)

<p><kbd><img width=1008 height=646 src="../images/IAM User Access Key.png" alt="IAM User Access Key"/></kbd></p>
    
## Code Level Configuration
If you want to use this code, you need to create your own resources, and define them in the beginning
of the class. For example, to test the S3 Folder Level security strategy, you would need to 
update the following variables based on the configuration of your own S3 bucket.

        // Tenant we are allowing access
        String myTenant = "YOUR-FIRST-TENANT-NAME;
        
        // Second tenant, use to show access denied to first tenants resources
        String otherTenant = "YOUR-SECOND-TENANT-NAME";

        // S3 Variable
        String bucket = "YOUR-BUCKET-NAME";

### Packaging Artifact

To create the JAR file we execute, run the following command. This will build all the modules in this
project, and this module depends on the Engine module.

    mvn -f .. clean package
    
Remember you will need to rebuild the jar after any code changes.

### Running the example

Use the access key and secret we create above for user "assuming-user-1".
   
    export AWS_ACCESS_KEY_ID=AKIA****************
    export AWS_SECRET_ACCESS_KEY=CF+***************************
    export region=us-west-2
    export account_id=123456789012  (your AWS Account ID)

    mvn clean package
    java -jar target/command-line-example-jar-with-dependencies.jar $account_id $region
    