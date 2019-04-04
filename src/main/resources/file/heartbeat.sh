set -e

afileHeartbeat="/tmp/heartbeat"
afileLog="/var/log/heartbeat.log"
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
