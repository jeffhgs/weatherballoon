rm -f "/tmp/heartbeat"

cat > /tmp/heartbeat.sh <<"EOF1"
#!/bin/bash
set -e

afileHeartbeat="/tmp/heartbeat"
afileLog="/var/log/heartbeat.log"
numMinutes=15
afileGfind="/usr/bin/find"
cmdShutdown=(/sbin/shutdown --poweroff now)

log() {
    line="$1"
    echo "$line" >> "$afileLog"
}

if [[ ! -e "$afileHeartbeat" ]]
then
  touch "$afileHeartbeat"
  chmod 777 "$afileHeartbeat"
fi
if [[ ! -e "$afileHeartbeat" ]]
then
  log "*** ERROR ***: could not create file: $afileHeartbeat"
  exit 1
fi
numStale=$("$afileGfind" "$afileHeartbeat" -maxdepth 1 -type f -not -newermt "${numMinutes} minute ago" | wc -l)
if [[ "$numStale" -gt 0 ]]
then
  log "found $numStale stale files"
  ${cmdShutdown[*]} 1>> "$afileLog" 2>> "$afileLog"
else
  log "heartbeat ok"
fi
EOF1
install /tmp/heartbeat.sh /usr/local/bin/heartbeat.sh
rm /tmp/heartbeat.sh

echo "* * * * * /usr/local/bin/heartbeat.sh" > /tmp/heartbeat.cron
crontab -u root /tmp/heartbeat.cron
rm /tmp/heartbeat.cron



cat > /tmp/with_heartbeat.sh <<"EOF2"

afileHeartbeat=/tmp/heartbeat
afileGtimeout=/usr/bin/timeout
afileTouch=/usr/bin/touch
durationMax="$1"
shift

cmd="$@"
"$afileGtimeout" --foreground ${durationMax} ${cmd[*]} &
pid=$!
status="0"
if [[ "${pid}" ]]
then
  while [[ "$status" == "0" ]]
  do
    "$afileTouch" "$afileHeartbeat"
    ps ${pid} 1> /dev/null 2> /dev/null
    status="$?"
    #echo pid=${pid} status=${status}
    sleep 2
  done
fi
#echo exiting
EOF2

sudo install /tmp/with_heartbeat.sh /usr/local/bin/with_heartbeat.sh
rm -f /tmp/with_heartbeat.sh



cat > /tmp/with_instance_role.sh <<"EOF3"
#!/bin/bash
role="$1"
shift
cmd="$@"

js=$(curl --silent "http://169.254.169.254/latest/meta-data/iam/security-credentials/${role}")

export AWS_ACCESS_KEY_ID=$(echo "$js" | jq -r ".AccessKeyId")
export AWS_SECRET_ACCESS_KEY=$(echo "$js" | jq -r ".SecretAccessKey")
export AWS_SESSION_TOKEN=$(echo "$js" | jq -r ".Token")

exec ${cmd[*]}
EOF3

sudo install /tmp/with_instance_role.sh /usr/local/bin/with_instance_role.sh
rm -f /tmp/with_instance_role.sh



cat > /tmp/rclone.sh <<"EOF4"
#!/bin/bash
env RCLONE_CONFIG_MYS3_TYPE=s3 rclone --config /dev/null --s3-env-auth "$@"
EOF4

sudo install /tmp/rclone.sh /usr/local/bin/rclone.sh
rm -f /tmp/rclone.sh
