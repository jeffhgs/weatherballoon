
echo "* * * * * /usr/local/bin/heartbeat.sh" > /tmp/heartbeat.cron
crontab -u root /tmp/heartbeat.cron
rm /tmp/heartbeat.cron

echo done >> /tmp/userdata-done
install /tmp/userdata-done /var/log/userdata-done
rm -f /tmp/userdata-done
