package ch.eswitch.tinylog.writers;

import java.util.Arrays;
import java.util.Map;

import org.tinylog.core.LogEntry;
import org.tinylog.writers.AbstractFormatPatternWriter;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;

/**
 * tinylog 2 AWS CloudWatch Logs Writer based on Amazon SDK for Java 2.x<br/>
 * This is a <a href="https://tinylog.org/v2/extending/#custom-writer">custom writer</a> for
 * <a href="https://tinylog.org/v2/">tinylog 2</a> logging framework to send log events to AWS CloudWatch Logs.<br/>
 * see <a href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html">AWS SDK for Java
 * 2.0</a><br/>
 * <br/>
 * {@link DefaultCredentialsProvider} is used for credentials<br/>
 * <br/>
 * see <a href=
 * "https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain">https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html#credentials-chain</a>
 * for using credentials
 *
 * @author Martin Schelldorfer, 2022
 */
public class AwsCloudWatchLogsWriter extends AbstractFormatPatternWriter
{

    private final CloudWatchLogsClient logsClient;
    /**
     * The name of the log group<br/>
     * see {@link PutLogEventsRequest#logGroupName()}
     */
    public String logGroupName;
    /**
     * The name of the log stream<br/>
     * see {@link PutLogEventsRequest#logStreamName()}
     */
    public String streamName;

    /**
     * writer property prefix for {@link software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider}<br/>
     */
    private final String PROPERTY_AWS = "aws.";

    /**
     * @param properties Configuration for writer
     */
    public AwsCloudWatchLogsWriter(Map<String, String> properties) throws Exception
    {
        super(properties);

        logGroupName = getStringValue("logGroupName");

        if (logGroupName == null
                || logGroupName.length() == 0)
        {
            throw new Exception("parameter 'logGroupName' must be set in tinylog writer configuration");
        }

        streamName = getStringValue("streamName");

        if (streamName == null
                || streamName.length() == 0)
        {
            throw new Exception("parameter 'streamName' must be set in tinylog writer configuration");
        }

        properties.forEach((key, value) -> {
            if (key.startsWith(PROPERTY_AWS))
                System.setProperty(key, value);
        });

        // example
        // https://docs.aws.amazon.com/code-samples/latest/catalog/javav2-cloudwatch-src-main-java-com-example-cloudwatch-PutLogEvents.java.html
        logsClient = CloudWatchLogsClient.builder()
                                         .credentialsProvider(DefaultCredentialsProvider.create())
                                         .build();
    }

    @Override
    public void write(LogEntry logEntry) throws Exception
    {
        try
        {
            DescribeLogStreamsRequest logStreamRequest = DescribeLogStreamsRequest.builder()
                                                                                  .logGroupName(logGroupName)
                                                                                  .logStreamNamePrefix(streamName)
                                                                                  .build();
            DescribeLogStreamsResponse describeLogStreamsResponse = logsClient.describeLogStreams(logStreamRequest);

            // Assume that a single stream is returned since a specific stream name was specified in the previous request.
            String sequenceToken = describeLogStreamsResponse.logStreams()
                                                             .get(0)
                                                             .uploadSequenceToken();

            String msg = render(logEntry);
            // Build an input log message to put to CloudWatch.
            InputLogEvent inputLogEvent = InputLogEvent.builder()
                                                       .message(msg)
                                                       .timestamp(System.currentTimeMillis())
                                                       .build();

            // Specify the request parameters.
            // Sequence token is required so that the log can be written to the
            // latest location in the stream.
            PutLogEventsRequest putLogEventsRequest = PutLogEventsRequest.builder()
                                                                         .logEvents(Arrays.asList(inputLogEvent))
                                                                         .logGroupName(logGroupName)
                                                                         .logStreamName(streamName)
                                                                         .sequenceToken(sequenceToken)
                                                                         .build();

            logsClient.putLogEvents(putLogEventsRequest);

        } catch (CloudWatchException e)
        {
            System.err.println(e.awsErrorDetails()
                                .errorMessage());
        }
    }

    @Override
    public void flush() throws Exception
    {

    }

    @Override
    public void close() throws Exception
    {
        logsClient.close();
    }
}
