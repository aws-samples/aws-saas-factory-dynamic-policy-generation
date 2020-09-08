#!/bin/bash

# We grab the output variables from CloudFormation
read -p "What is the stack name for the CloudFormation you just created?" STACK_NAME
if [ -z "STACK_NAME" ]; then
	echo "CloudFormation stack name is empty. Exiting."
	exit 1
fi

# We grab the output variables from CloudFormation
read -p "Which username are you using to login?" USERNAME
if [ -z "USERNAME" ]; then
	echo "Username is empty. Exiting."
	exit 1
fi

USER_POOL=$(aws cloudformation describe-stacks --stack-name $STACK_NAME \
      --query "Stacks[0].Outputs[?OutputKey=='UserPool'].OutputValue" --output text)
USER_POOL_CLIENT=$(aws cloudformation describe-stacks --stack-name $STACK_NAME \
      --query "Stacks[0].Outputs[?OutputKey=='UserPoolClient'].OutputValue" --output text)

echo "Authenticating user $USERNAME"
AUTH_RESULT=$(aws cognito-idp admin-initiate-auth --user-pool-id "$USER_POOL" --client-id "$USER_POOL_CLIENT" --auth-flow ADMIN_NO_SRP_AUTH --auth-parameters USERNAME="$USERNAME",PASSWORD=ABCdef123)
JWT_ID_TOKEN=$(echo $AUTH_RESULT | jq -r '.AuthenticationResult.IdToken')

echo $JWT_ID_TOKEN
echo
