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


cat > /tmp/with_sync_updown.sh <<"EOF5"
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
/usr/local/bin/with_heartbeat.sh ${minutesMaxRun}m bash /usr/local/bin/with_instance_role.sh ${nameOfRole} /usr/local/bin/rclone.sh --s3-region us-west-2 sync ${adirServer}/log mys3:${dirStorage}/log/${idrun}

EOF5
sudo install /tmp/with_sync_updown.sh /usr/local/bin/with_sync_updown.sh
rm -f /tmp/with_sync_updown.sh


cat > /tmp/with_logging.sh <<"EOF6"
#!/bin/bash

adirLog="$1"
shift

"$@" 2>&1 | tee -a "$adirLog"
EOF6
sudo install /tmp/with_logging.sh /usr/local/bin/with_logging.sh
rm -f /tmp/with_logging.sh


cat > /tmp/spool_via_tmux.sh <<"EOF7"
#!/bin/bash
s=weatherballoon

if ! tmux has-session -t "$s" 2>&-
then
  tmux new-session -d -n "$s" -s "$s"
fi
x="$@"
tmux send-keys -t "$s" "$x"
tmux send-keys -t "$s" Enter
EOF7
sudo install /tmp/spool_via_tmux.sh /usr/local/bin/spool_via_tmux.sh
rm -f /tmp/spool_via_tmux.sh


echo done >> /tmp/userdata-done
install /tmp/userdata-done /var/log/userdata-done
rm -f /tmp/userdata-done
