#!/bin/bash

# Generate a new random Tenant ID
read -p "What is the tenantID, leave blank for random? " TENANT_ID
if [ -z "$TENANT_ID" ]; then
  TENANT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]'| cut -c 1-8)
fi

# We grab the output variables from CloudFormation
read -p "What is the stack name for the CloudFormation you just created?" STACK_NAME
if [ -z "STACK_NAME" ]; then
	echo "CloudFormation stack name is empty. Exiting."
	exit 1
fi

TENANT_USERNAME="user$(uuidgen | cut -c 1-4)@example${TENANT_ID}.com"

TENANT_USER_POOL=$(aws cloudformation describe-stacks --stack-name $STACK_NAME \
      --query "Stacks[0].Outputs[?OutputKey=='UserPool'].OutputValue" --output text)
TENANT_USER_POOL_CLIENT=$(aws cloudformation describe-stacks --stack-name $STACK_NAME \
      --query "Stacks[0].Outputs[?OutputKey=='UserPoolClient'].OutputValue" --output text)
TENANT_IDENTITY_POOL=$(aws cloudformation describe-stacks --stack-name $STACK_NAME \
      --query "Stacks[0].Outputs[?OutputKey=='IdentityPool'].OutputValue" --output text)

if [ -z "TENANT_USER_POOL" ]; then
	echo "Cognito user pool is empty. CloudFormation failed, exiting."
	exit 1
fi

# Create a new user in the User Pool skipping the usual registration email/password cycle
TENANT_COGNITO_USER=$(aws cognito-idp admin-create-user --user-pool-id "$TENANT_USER_POOL" \
      --username "$TENANT_USERNAME" --message-action SUPPRESS \
      --user-attributes "[{\"Name\":\"custom:tenant_id\",\"Value\":\"$TENANT_ID\"},{\"Name\":\"custom:identity_pool\",\"Value\":\"$TENANT_IDENTITY_POOL\"}]")
if [ -z "$TENANT_COGNITO_USER" ]; then
	echo "Cognito admin-create-user failed. Exiting."
	exit 1
fi

# Set the user's password and status to active
aws cognito-idp admin-set-user-password --user-pool-id "$TENANT_USER_POOL" --username "$TENANT_USERNAME" \
      --password ABCdef123 --permanent

# Now we can log in as the user to the User Pool
AUTH_RESULT=$(aws cognito-idp admin-initiate-auth --user-pool-id "$TENANT_USER_POOL" \
      --client-id "$TENANT_USER_POOL_CLIENT" --auth-flow ADMIN_NO_SRP_AUTH \
      --auth-parameters USERNAME="$TENANT_USERNAME",PASSWORD=ABCdef123)
JWT_ID_TOKEN=$(echo $AUTH_RESULT | jq -r '.AuthenticationResult.IdToken')

HTTP_API=$(aws cloudformation describe-stacks --stack-name $STACK_NAME \
      --query "Stacks[0].Outputs[?OutputKey=='HttpApiUrl'].OutputValue" --output text)

echo "We've create user $TENANT_USERNAME for Tenant $TENANT_ID"

echo "You can use the curl command below to test. It is pre-loaded with the JWT token for Tenant $TENANT_ID."

echo "curl --location --request GET '$HTTP_API/jwt-simple-flow' --header 'x-tenant-id: $TENANT_ID' --header 'Authorization: Bearer $JWT_ID_TOKEN'"
