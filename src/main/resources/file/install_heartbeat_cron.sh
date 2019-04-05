
echo "* * * * * /usr/local/bin/heartbeat.sh" > /tmp/heartbeat.cron
crontab -u root /tmp/heartbeat.cron
rm /tmp/heartbeat.cron

echo done >> /tmp/userdata-done
install /tmp/userdata-done "$afileDone"
rm -f /tmp/userdata-done
