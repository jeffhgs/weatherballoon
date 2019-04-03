#!/bin/bash
role="$1"
shift
cmd="$@"

js=$(curl --silent "http://169.254.169.254/latest/meta-data/iam/security-credentials/${role}")

export AWS_ACCESS_KEY_ID=$(echo "$js" | jq -r ".AccessKeyId")
export AWS_SECRET_ACCESS_KEY=$(echo "$js" | jq -r ".SecretAccessKey")
export AWS_SESSION_TOKEN=$(echo "$js" | jq -r ".Token")

exec ${cmd[*]}
