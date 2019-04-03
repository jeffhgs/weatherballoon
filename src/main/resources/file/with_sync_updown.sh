#!/bin/bash

minutesMaxRun="$1"
shift
nameOfRole="$1"
shift
dirStorage="$1"
shift
adirServer="$1"
shift
idrun="$1"
shift

/usr/local/bin/with_heartbeat.sh ${minutesMaxRun}m bash /usr/local/bin/with_instance_role.sh ${nameOfRole} /usr/local/bin/rclone.sh --s3-region us-west-2 sync mys3:${dirStorage}/srchome ${adirServer}
rm -rf ${adirServer}/log
mkdir -p ${adirServer}/log
cd ${adirServer}
/usr/local/bin/with_heartbeat.sh ${minutesMaxRun}m "$@"
/usr/local/bin/with_heartbeat.sh ${minutesMaxRun}m bash /usr/local/bin/with_instance_role.sh ${nameOfRole} /usr/local/bin/rclone.sh --s3-region us-west-2 copy ${adirServer}/log mys3:${dirStorage}/log/${idrun}
