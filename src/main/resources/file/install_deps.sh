# TODO: better fix for userdata-done baked into the AMI
rm -f "$afileDone"

apt-get update
apt-get install -y \
    ec2-api-tools \
    jq
curl https://rclone.org/install.sh | bash
apt-get remove -y \
    default-jre-headless \
    openjdk-11-jre-headless
apt-get install -y \
    openjdk-8-jre-headless \
    openjdk-8-jdk-headless

/usr/bin/python3 /usr/bin/unattended-upgrade