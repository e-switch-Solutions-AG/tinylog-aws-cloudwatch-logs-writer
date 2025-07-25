<!-- this file must be edited in folder src/main/resources -->
<!-- the file in root folder (basedir) will be overwritten on maven install phase -->

# AWS CloudWatch Logs Writer

This is a [custom writer](https://tinylog.org/v2/extending/#custom-writer) for
[tinylog 2](https://tinylog.org/v2/) logging framework to send log events
to [AWS CloudWatch Logs](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/WhatIsCloudWatchLogs.html).

[AWS SDK for Java 2.0](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html) is used to send log
events to AWS CloudWatch Logs

### Message Formats

Two different types of message formats are supported:

- [Format Pattern](#FormatPatternID)
- [JSON Format](#JsonFormatID)

## Installation

### Maven

```
<dependency>
    <groupId>ch.eswitch.tinylog.writers</groupId>
    <artifactId>tinylog-aws-cloudwatch-logs-writer</artifactId>
    <version>1.0.23</version>
</dependency>
```

### Gradle

```
compile(group: 'ch.eswitch.tinylog.writers', name: 'tinylog-aws-cloudwatch-logs-writer', version: '1.0.23', ext: 'pom')
```

### Build Repository

https://artifactory.e-switch.ch/artifactory/libs-release-public

(add this repository to `repositories` section in your `pom.xml` or `build.gradle`)

## Configuration

### Writer name

#### Format Pattern

to use Format Pattern, use the following writer name in tinylog writer configuration:<br/>
`aws cloud watch logs`

see [Message Format - Format Pattern](#FormatPatternID) for configuration

#### JSON Format

to use JSON Format, use the following writer name in tinylog writer configuration:<br/>
`aws cloud watch logs json`

see [Message Format - JSON Format](#JsonFormatID) for configuration

### AWS configuration

#### Log Group and Log Stream

Property `logGroupName` and `streamName` are mandatory and must be specified in tinylog configuration writer
config.<br/>
see [Working with log groups and log streams](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/Working-with-log-groups-and-streams.html)

Log Group must exist.

In case a Log Stream does not exist, it will be created (*since 1.0.13*).

#### Authentication

Class [software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/auth/credentials/DefaultCredentialsProvider.html)
is used for AWS authentication.
see [AWS Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain)
how to configure credentials

Properties starting with `aws.` in tinylog configuration writer config are read and forwarded
to [software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/auth/credentials/SystemPropertyCredentialsProvider.html)

### Message Format

#### <a id="FormatPatternID"/>Format Pattern

[Message Formatter](https://tinylog.org/v2/configuration/#format-pattern) (`format` property) is supported and can be
used to format message.

Formatting is based on
class [`org.tinylog.writers.AbstractFormatPatternWriter`](https://github.com/tinylog-org/tinylog/blob/v2.5/tinylog-impl/src/main/java/org/tinylog/writers/AbstractFormatPatternWriter.java)

*Example of `tinylog.properties`:*

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

#### <a id="JsonFormatID"/>JSON Format

[JSON Writer](https://tinylog.org/v2/configuration/#json-writer) is used and can be used for JSON configuration.<br/>
Properties `file`, `charset`, `append` and `buffered` are NOT supported.

Formatting is done in the same way as in
class [`org.tinylog.writers.JsonWriter`](https://github.com/tinylog-org/tinylog/blob/v2.5/tinylog-impl/src/main/java/org/tinylog/writers/JsonWriter.java)

*Example of `tinylog.properties`:*

```
writer_awscloudwatchlogsjson=aws cloud watch logs json
writer_awscloudwatchlogsjson.level=trace
writer_awscloudwatchlogsjson.format=LDJSON
writer_awscloudwatchlogsjson.field.level=level
writer_awscloudwatchlogsjson.field.thread=thread
writer_awscloudwatchlogsjson.field.source={class}.{method}()
writer_awscloudwatchlogsjson.field.message=message
writer_awscloudwatchlogsjson.logGroupName=myLogGroup
writer_awscloudwatchlogsjson.streamName=myStream
writer_awscloudwatchlogsjson.aws.region=eu-central-1
writer_awscloudwatchlogsjson.aws.accessKeyId=AKIAxxxxxxxxxxxxxxxx
writer_awscloudwatchlogsjson.aws.secretAccessKey=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
                                             
```

### Log Event Size

**Log event size is limited to 256kB by AWS CloudWatch** and can't be changed (
see [CloudWatch Logs quotas](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/cloudwatch_limits_cwl.html)).

Boolean property `splitLargeMessages` controls how to handle larger messages (&gt; 256kB).

If this property is not set (default behaviour), log message text is truncated.

If this property is set, log messages are split into several messages with same timestamp.
Each message is marked with context key `part` and value `[<part>/<total parts>]`.

## AWS Log Viewer

HTTP
Servlet [`ch.eswitch.tinylog.writers.AwsCloudWatchLogsViewerServlet`](blob/main/src/main/java/ch/eswitch/tinylog/writers/AwsCloudWatchLogsViewerServlet.java)
provides web based access to AWS Log Event with various select options.

This Servlet merges split Log Events into one.

This Servlet is availble on URL ["web context"/awslogviewer](/awslogviewer).






