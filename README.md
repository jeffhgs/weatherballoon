
# Weatherballoon

Weatherballoon: The fastest inexpensive way to get your new experiment into the clouds.

![](doc/Shot_from_High_Altitude_Ballon_with_Lake_Michigan.JPG)

Image from a hobby high-altitude balloon.  Image copyright [Noah Klugman](https://en.wikipedia.org/wiki/High-altitude_balloon#/media/File:Shot_from_High_Altitude_Ballon_with_Lake_Michigan.JPG)

# Synopsis

Weatherballoon takes any local machine command line run/test command and efficiently offloads the command's execution to cloud compute resources.  Weatherballoon is especially well adapted to use with high end cloud computing resources, such as using cloud instances equipped with specialized GPU/TPU hardware to run deep learning model training.

<!-- TOC depthFrom:1 depthTo:6 withLinks:1 updateOnSave:1 orderedList:0 -->

- [Weatherballoon](#weatherballoon)
- [Synopsis](#synopsis)
- [Requirements](#requirements)
- [Installation](#installation)
	- [Prerequisites](#prerequisites)
	- [Installation Instructions](#installation-instructions)
		- [Configuration](#configuration)
			- [Configuration: ssh](#configuration-ssh)
		- [Authorization](#authorization)
			- [Authorization: AWS](#authorization-aws)
				- [Authorization: AWS: User, quick and dirty](#authorization-aws-user-quick-and-dirty)
				- [Authorization: AWS: Role, quick and dirty](#authorization-aws-role-quick-and-dirty)
				- [Authorization: AWS: least privilege](#authorization-aws-least-privilege)
	- [Usage](#usage)
	- [Known Issues](#known-issues)
	- [Development](#development)
- [Revision History](#revision-history)
	- [Release 0.1:](#release-01)
- [Release Roadmap](#release-roadmap)
	- [Future release 0.9: Error handling, configuration, and documentation](#future-release-09-error-handling-configuration-and-documentation)
	- [Future release 1.0: A second cloud provisioner](#future-release-10-a-second-cloud-provisioner)
	- [Future releases past 1.0](#future-releases-past-10)

<!-- /TOC -->

# Requirements

- Run on high end cloud compute resources with confidence that you will not accidentally overspend
- Automatically stop spending, limited only by the failure modes of the underlying cloud services
- Easy installation: easily run on MacOS, Windows, and Linux, using well-established abstraction layers.  Therefore, prefer embedding a dependency instead of depending on a local binary which might pose portability problems or too many degrees of configuration freedom, e.g. openssh.
- Pluggable cloud service provisioner backends
- Heartbeat-based failure detection
- Unified configuration
- Be efficient with:
    - Incurred fees for cloud resources
    - Wall clock time

# Installation

## Prerequisites

Current local prerequisites are:

  - JRE 1.8
  - One portable third-party binary (rclone, a golang based cloud file synchronizer)
  - Credentials on one cloud compute service

Current remote OS prerequisites are:

  - Ubuntu linux is the target test OS.  Other linux-based distributions may work but are not currently tested.

Current supported cloud compute services are:

  - Amazon Elastic Compute Cloud (AWS EC2)

## Installation Instructions

  - 1 - Unzip the distribution (currently 3 files) into a location in your path
  - 2 - Install rclone
  - 3 - run weatherballoon.sh

## Usage

At this time, all weatherballoon configuration is done using the configuration files.

Weatherballoon command line invocation is as follows:

    weatherballoon.sh -- <command to run remotely>

If a remote compute instance is currently available, it will be used to run the command.  If no remote compute instance is available, one will be created, and then used to run the command.

### Configuration

Specify your configuration with a file named <code>.weatherballoon.json</code>

Weatherballoon finds the <code>.weatherballoon.json</code> file starting with the current directory, and then with each parent of the current working directory.

An example <code>.weatherballoon.json</code> file is located in doc/sample_.weatherballoon.json:

    {
      "provisioner":{
        "kind": "aws",
        "region": "us-west-2",
        "group1": "sg_temp1",
        "keyPair": "id_gs_temp_2019-01",
        "os": {
          "ami": "ami-0e3e4660d8725dd31",
          "username": "ubuntu"
        },
        "instanceType": "t2.medium",
        "cred": null,
        "roleOfInstance":
          "arn:aws:iam::............:instance-profile/weatherballoon-ec2-accesses-s3all"
      },
      "tag": "Remoter",
      "sync": {
        "adirLocal": null,
        "fileExcludes": null,
        "adirServer": "/home/ubuntu/srchome",
        "dirStorage": "weatherballoon-test1"
      }
    }

Specify your credentials with a file named <code>.weatherballoon_cred.json</code>

Weatherballoon finds the <code>.weatherballoon.json</code> file starting with the current directory, and then with each parent of the current working directory.

An example <code>.weatherballoon.json</code> file:

    {
      "id": ". . .",
      "secret": ". . ."
    }

#### Configuration: ssh

Weatherballoon embeds its own ssh client.  The client is configured to read openssh-style private key files.  The private key files should be put in the directory ~/.ssh/name_of_key.

For AWS, the public part of the key should be uploaded to the AWS console named with "name_of_key", same as the file.

### Authorization
#### Authorization: AWS
##### Authorization: AWS: User, quick and dirty

Two credentials are required: a user for running on your local machine, and a role for EC2.

Create an IAM user for "Programmatic access", to run on your local machine.  For more information about creating users for programmatic access, consult the IAM user guide:

> https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_create.html

Deposit the user's access keys into a new file ~/.weatherballoon_cred.json:

    {
      "id": "...",
      "secret": "..."
    }

Set permissions of ~/.weatherballoon_cred.json to be visible only to the current user.  (Hint: compare permissions to ~/.ssh/id_rsa)

Grant the user the following policies:

    AmazonEC2FullAccess
    AmazonS3FullAccess
    IAMFullAccess

##### Authorization: AWS: Role, quick and dirty

Now we need to create the role for ec2 to run weatherballoon.

  - Go to the AWS Web Console
  - Under Services, select "IAM"
  - Under Roles, select "Create role"
  - Under "Select the type of trusted entity", select "AWS Service"
  - Under "Choose the service that will use this role", select "EC2"
  - Select "Next: Permissions"
  - In the search box, type "s3full", check "AmazonS3FullAccess", and select "Next: Tags"
  - Select "Next: Review"
  - Under "Role name", select "weatherballoon-ec2-accesses-s3all"
  - Select "Create role"

Now AWS has the role for running weatherballoon and we need to tell weatherballoon about it.

  - If you have not already, copy the example doc/sample_.weatherballoon.json to .weatherballoon.json in the directory where you plan to work.
  - Open .weatherballoon.json for editing
  - Under IAM, under roles, the table will have a new entry.
  - Select the hyperlink for "weatherballoon-ec2-accesses-s3all"
  - Under Role ARN, select and copy the text <code>arn:aws:iam::............:instance-profile/weatherballoon-ec2-accesses-s3all</code>
  - Make sure you did not copy the text <code>arn:aws:iam::............:role/weatherballoon-ec2-accesses-s3all</code>
  - Paste the role ARN into your .weatherballoon.json, under the field "arn"

##### Authorization: AWS: least privilege

Authorization can be weakened as follows:

The user and role need only write to the s3 buckets specified in .weatherballoon.json

The user does not really need full IAM access, but only:

    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Action": "iam:PassRole",
                "Resource": "arn:aws:iam::............:role/*"
            }
        ]
    }

Remember to insert your own account number when granting this policy.

Note that the arn in the policy contains "role/" not "instance-policy/".

Note that the "*" can be further restricted to passing of the specific role you have created.

## Known Issues

  - The number of minutes before termination is hardcoded as numMinutes=15 in <code>src/main/resources/file/install_heartbeat_cron.sh</code>
  - Credentials cannot be encrypted
  - There is only one cloud provisioner  (AWS)

## Development

To recompile weatherballoon, use the following gradle command:

    ./gradlew zip

The distribution zip will be built at <code>build/dist.zip</code>


# Revision History

## Release 0.1:

  - AWS EC2 backend implementation
  - Server shell scripts compatible with Ubuntu
  - Basic configuration
  - JCE extensions workaround: Distribute binaries as two jar files instead of one
  - Create README.md file

# Release Roadmap    

## Future release 0.9: Error handling, configuration, and documentation

  - Abstraction layer for pluggable cloud backends
  - Helpful messaging for authentication, authorization, and configuration errors
  - Validate prerequisite rclone installation
  - Semantic versioning for configuration file
  - Distribution as one jar file not two
  - Configure installed shell scripts from unified configuration file to admit target OSes other than ubuntu
  - Documentation: Reference
  - Documentation: Installation guide

## Future release 1.0: A second cloud provisioner

  - Automation for authorization according to principle of least privilege
  - Implement a second cloud compute backend

## Future releases past 1.0

  - More cloud vendor backends
  - Extension point to enable low latency log aggregation and streaming (E.g. cloudwatch logs)
  - Extension point for extra supervision features based on serverless function services (E.g. AWS Lambda)
  - Storage of credentials in workstation keychain
  - Configuration of remote OS images that is invariant of cloud provisioner
