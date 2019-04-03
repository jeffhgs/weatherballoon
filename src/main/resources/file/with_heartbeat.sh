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
