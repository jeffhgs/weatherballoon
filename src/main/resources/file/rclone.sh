#!/bin/bash
env RCLONE_CONFIG_MYS3_TYPE=s3 rclone --config /dev/null --s3-env-auth "$@"
