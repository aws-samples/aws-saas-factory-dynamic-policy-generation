## Introduction 

SaaS organizations leverage IAM Roles and Policies as the backbone of their tenant isolation strategies. Dynamic Policy 
Generation is a technique to ease the burden of static policy management, while enforcing tenant isolation. 
We will show an example implementation using AWS Lambda (Lambda) to access tenant specific resources on Amazon S3 (S3) 
and Amazon DynamoDB (DynamoDB). The example will show how our policies allow access tenant specific resources, but 
enforce our isolation boundary, denying access to other tenants' resources.

The sample Lambda utilizes a Layer we call a Token Vending Machine to exchange the Identity Providers authentication 
token, a [JWT](https://jwt.io/) in this example, for a fully scoped AWS Credential. This credential can only be 
used to access the tenant specific resources we defined using permission templates for restricting access to:
 
 1. a tenant folder (or prefix) in S3
 2. DynamoDB records containing the tenant identifier as the leading key in a table
 
The Token Vending Machine call will hydrate our templates with our tenant, extracted from the claims of our JWT, and
any other fields required, and create an IAM Policy. The resulting policy will be passed to AWS Security Token 
Service (STS), which will allow us to assume a IAM role, and return our fully scoped credential.

The included sample utilizes Amazon Cognito as it's identity provider. A Cognito user pool will be set up to provide
authentication of tenant users we will set up. However, take note that the Dynamic Policy Generation technique can
work with a variety of Identity Provider, as long as we can derive tenant identity from their provided authentication token.

## Architecture

The sample includes three Lambdas which implement the Token Vending Machine concept. Each variation shows how a
different architecture can achieve our token isolation goals. These are each exposed in a different endpoint they we
will discuss below.

### Code Authorization

This diagram shows JWT verification and claims extraction implemented in our code.

<p><kbd><img width=995 height=246 src="./images/Simple JWT Flow.png" alt="Simple JWT Flow"/></kbd></p>

This diagram shows JWT verification and claims extraction implemented as a JWT Authenticator in the API Gateway.

<p><kbd><img width=990 height=240 src="./images/API Gateway Authorizer Flow.png" alt="API Gateway Authorizer Flow"/></kbd></p>

This diagram shows JWT verification and claims extraction implemented in our code, as well as utilizing a Cognito 
Identity Pool to retrieve a role.

<p><kbd><img width=1002 height=319 src="./images/Cognito Identity Pool Flow.png" alt="Cognito Identity Pool Flow"/></kbd></p>


## Prerequisites

This sample is written in Java. You will need a functioning Java 11 install and Maven 3.x or newer to compile the Lambda 
functions example. You will also need an AWS account that you have administrative access to in order to run the 
CloudFormation templates.

## Getting Started
(1) You will need an S3 bucket in the same Region where you're going to deploy the sample. You can use an existing bucket 
or create a new one.

(2) Clone the repository and, using Maven (or your favorite IDE with Maven support), clean and package the 4 lambda projects.

```shell
git clone https://github.com/aws-samples/aws-saas-factory-dynamic-policy-generation.git aws-saas-factory-dynamic-policy-generation
cd aws-saas-factory-dynamic-policy-generation
mvn
```

(3) Upload the CloudFormation template, and the Lambda packages to your S3 bucket.

```shell
BUCKET=name-of-your-s3-bucket

# Upload the Lambda and Layer code packages we just compiled
find . -type f \( -name '*lambda*.jar' -o -name '*layer*.jar' \) ! -name 'original-*.jar' -exec aws s3 cp {} s3://$BUCKET \;

# Upload the CloudFormation template
aws s3 cp cognito-lambda-example/cognito-user-role-bootstrap.yml s3://$BUCKET
```

(4) Deploy the template using CloudFormation. You will need to enter the S3 Bucket you uploaded to above, as well as 
a name for a new multi-tenant S3 bucket, and a name for a DynamoDB table we will create for testing.
You may find the AWS Console more convenient than the command line. Remember the stack name you choose, you'll need
it in the next step.

<p><kbd><img width=975 height=507 src="./images/Cognito CF Variables.png" alt="Cognito CloudFormation Variables"/></kbd></p>

This template will create the following resources
* An S3 bucket for multi-tenant testing
* A DynamoDB table for testing
* Lambda Functions
* A Lambda Layer
* HTTP APIs 
* A Cognito User Pool and Client
* A Cognito Identity Pool
* An IAM Role for the Identity Pool, which our code to assume
* An IAM Role for our code to look up by ARN and assume
* An IAM Role allowing our Lambda to execute

## Testing the Example

(1) Create a user for the Cognito User Group we created. A user allows us to login, and create a JWT token for
testing. The only thing you'll need is the CloudFormation stack name you created above.

```
sh cognito-lambda-example/create-tenant-cognito-user.sh 

What is the tenantID, leave blank for random? 123456789
Generated random tenantId = 123456789

Tenant 123456789

What is the stack name for the CloudFormation you just created? your-stack-name
```

(2) The output of the script is a curl statement. Copy the curl command, and execute it as many times as you like:

```
We've create user userXXXXXX@example.com for Tenant 123456789

You can use the curl command below to test. It is pre-loaded with the JWT token for Tenant 123456789.

curl --location --request GET 'https://xxxxxxxxxx.execute-api.us-west-2.amazonaws.com/jwt-simple-flow' 
--header "X-Tenant-ID: 123456789" --header 'Authorization: Bearer eyJr...bx2PaScA'

```

The response will be the current count of records in S3 and Dynamo that are visible to the tenant, similar to:

```
{
  "S3 documents visible" : "2",
  "DynamoDB rows visible" : "2",
  "tenant" : "8aff1cd9"
}
```

(3) Now modify your curl command change the header to any value, --header "X-Tenant-ID: xxxxxxxxx". This will cause the
code to attempt to retrieve items this tenant doesn't have access to.

(4) Confirm the results in your S3 console. You should see folders for each of the tenants you created while testing. 
The number of files in the folder matching the tenant your testing should match the number returned by the Lambda.

<p><kbd><img width=729 height=465 src="./images/S3 Bucket Folders.png" alt="S3 Bucket Folders"/></kbd></p>

<p><kbd><img width=872 height=488 src="./images/S3 Bucket Files.png" alt="S3 Bucket Files"/></kbd></p>

(5) You can create as many users and tenants as you like while testing, but after 15 minutes the JWT token in the
original curl will expire. You can log in to get a new JWT, and paste it into your test curl statements:

```
sh cognito-lambda-example/tenant-cognito-login.sh 

What is the stack name for the CloudFormation you just created? your-stack-name

Which username are you using to login? userXXXX@example.com

Authenticating user userXXXX@example.com

eyJraWQ...k-7PLqA
```

Now you just copy the string eyJraWQ...k-7PLqA (it's long) and replace the token in 
--header 'Authorization: Bearer eyJr...bx2PaScA' below:

```
curl --location --request GET 'https://xxxxxxxxxx.execute-api.us-west-2.amazonaws.com/jwt-simple-flow' 
--header "X-Tenant-ID: 123456789" --header 'Authorization: Bearer eyJr...bx2PaScA'
```

(6) The curl examples have all utilized a single AWS Lambda. This Lambda, which has the path /"jwt-simple-flow", 
utilizes code-based JWT authentication. This project also includes two other Lambdas which utilize other methods of 
JWT Authentication. If you want to try the other methods out, use the paths below:

* /api-gateway-auth = use tenant in the RequestContext added by API Gateway JWT Authenticator
* /cognito-identity = code based JWT authentication using Cognito Identity Pools
* /jwt-simple-flow  = code based JWT authentication

## Other Modules

These modules are libraries that can be used, extended, or borrowed from to create Token Vending
Machine solutions.

### Permission Templates

A collection of re-usable templates which each represent a
strategy for securing a service with tenant specific 
security.

The templates included with this project include the following security strategies.

* Amazon S3 - Bucket with Multiple Folders
* Amazon DynamoDB - Table with tenant leading key
* Amazon EFS - Access points tagged with tenant
* AWS Systems Manager Parameter Store - A parameter including tenant in path
* AWS Secret Manager - Secret tagged with the tenant
* Amazon SQS - A queue with the tenant in the name

Here is an example of a template for DynamoDB.

    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:*"
      ],
      "Resource": [
        "arn:aws:dynamodb:*:*:table/{{table}}"
      ],
      "Condition": {
        "ForAllValues:StringEquals": {
          "dynamodb: LeadingKeys": [ "{{tenant}}" ]
        }
      }
    }

Each template contains variables the Token Vending Machine needs to hydrate.  
In this case, the **table** and **tenant** variables need to be fulfilled.

### Policy Engine

This module is the core functionality responsible for implementation of a Token Vending Machine. This includes:

* Policy Generator - which loads Permission templates from a JAR file, and hydrates them with [Mustache](https://mustache.github.io/)
* A JWT Claims Extractor - which utilizes Cognito to verify the token
* Token Vendors - which are takes our generated policy and utilizes it for STS role assumption 
  * TokenVendor - which just takes in a Policy Generator, creates the policy and passes it to STS
  * JWTTokenVendor - which locates the tenant in the JWT in the Headers, creates the policy and passes it to STS
  * CognitoTokenVendor - which locates the tenant and a Cognito Identity Pool identifier in the JWT in the Headers, 
    creates the policy, obtains a IAM role from the Identity Pool, and passes the policy and role to STS
* An optional Cognito Identity Pool implementation - to facilitate authorization flows using Identity Pools

### Token Vending Layer

The lambda layer which abstracts Dynamic Policy Generation from the Lambdas that utilize it. From a callers' perspective
interacting with this layer should be a single call:

    TokenVendingMachine tokenVendingMachine = new TokenVendingMachine();
    AwsCredentialsProvider tenantCredentials = tokenVendingMachine.vendToken(input);
    
which takes in a token containing our tenant, and returns a fully scoped credential which can be used to call AWS services.

### Command Line Example

A simple tool for testing Dynamic Policy Generation from the command line. This example doesn't use Cognito, or any
other Identity Provider, so only the resources you want to secure need to exist in an AWS account for testing.

[See the instructions here](command-line-example/README.md)

This example contains code for testing all the Permission Templates included with this project.

## License

This library is licensed under the MIT-0 License. See the LICENSE file.
