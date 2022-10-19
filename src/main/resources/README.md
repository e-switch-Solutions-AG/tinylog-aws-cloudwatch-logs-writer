<!-- this file must be edited in folder src/main/resources -->
<!-- the file in root folder (basedir) will be overwritten on maven install phase -->

# AWS CloudWatch Logs Writer

This is a [custom writer](https://tinylog.org/v2/extending/#custom-writer) for
[tinylog 2](https://tinylog.org/v2/) logging framework to send log events
to [AWS CloudWatch Logs](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/WhatIsCloudWatchLogs.html).

[AWS SDK for Java 2.0](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html) is used to send log
events to AWS CloudWatch Logs

## Installation

### Maven

```
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>${project.artifactId}</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Gradle

```
compile(group: '${project.groupId}', name: '${project.artifactId}', version: '${project.version}', ext: 'pom')
```

### Build Repository

https://artifactory.e-switch.ch/artifactory/libs-release-public

(add this repository to `repositories` section in your `pom.xml` or `build.gradle`)

## Configuration

### Writer name

`aws cloud watch logs`

### AWS configuration

#### log group and log stream

Property `logGroupName` and `streamName` are mandatory and must be specified in tinylog configuration writer config.
see [Working with log groups and log streams](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/Working-with-log-groups-and-streams.html)

#### authentication

Class [software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/auth/credentials/DefaultCredentialsProvider.html)
is used for AWS authentication.
see [AWS Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain)
how to configure credentials

Properties starting with `aws.` in tinylog configuration writer config are read and forwarded
to [software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/auth/credentials/SystemPropertyCredentialsProvider.html)

### Message Formatter

[Message Formatter](https://tinylog.org/v2/extending/#custom-logging-api) (`format` property) is supported

### Example

example of `tinylog.properties`:

```
writer_awscloudwatchlogs=aws cloud watch logs
writer_awscloudwatchlogs.level=trace
writer_awscloudwatchlogs.format={{level}|min-size=7} [{thread}] {class}.{method}()\t{context: prefix}{message}
writer_awscloudwatchlogs.logGroupName=myLogGroup
writer_awscloudwatchlogs.streamName=myStream
writer_awscloudwatchlogs.aws.region=eu-central-1
writer_awscloudwatchlogs.aws.accessKeyId=AKIAxxxxxxxxxxxxxxxx
writer_awscloudwatchlogs.aws.secretAccessKey=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
                                             
```






