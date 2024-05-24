package ch.eswitch.tinylog.writers;

import org.tinylog.core.LogEntry;
import org.tinylog.writers.AbstractFormatPatternWriter;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * tinylog 2 AWS CloudWatch Logs Writer based on Amazon SDK for Java 2.x<br/>
 * This is a <a href="https://tinylog.org/v2/extending/#custom-writer">custom writer</a> for
 * <a href="https://tinylog.org/v2/">tinylog 2</a> logging framework to send log events to AWS CloudWatch Logs.<br/>
 * Message is formatted based on {@link AbstractFormatPatternWriter}<br/>
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
    /**
     * property name in tinylog configuration for {@link #logGroupName}
     */
    public static final String PROPERTY_LOG_GROUP_NAME = "logGroupName";

    /**
     * property name in tinylog configuration for {@link #streamName}
     */
    public static final String PROPERTY_STREAM_NAME = "streamName";

    /**
     * maximum message size<br/>
     * Log event size: 256 KB (maximum). This quota can't be changed.<br/>
     * see <a href="https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/cloudwatch_limits_cwl.html" target="_blank">CloudWatch Logs quotas</a>
     */
    final static int MAX_MESSAGE_SIZE = 250 * 1024;

    /**
     * text which is appended to message, in case message is longer than {@link #MAX_MESSAGE_SIZE}
     */
    static final String MESSAGE_TRUNCATED = "... (total message size was %,d)";

    /**
     * JSON 'message' attribute name
     */
    static final String JSON_MESSAGE_ATTRIBUTE = "message";

    /**
     * JSON 'context' attribute name
     */
    static final String JSON_CONTEXT_ATTRIBUTE = "context";

    /**
     * key in log entry context for partial messages<br/>
     * {@link LogEntry#getContext()}
     */
    static final String CONTEXT_KEY_PART = "part";

    /**
     * format for value of {@value #CONTEXT_KEY_PART}
     */
    static final String CONTEXT_PART_FORMAT = "[%d/%d]";

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
     * Boolean property to control large messages<br/>
     * If this property is set, large messages (&gt; 256kB) are split into several messages<br>
     * If this property is not set, large messages are truncated to allowed size (256kB)
     */
    public boolean splitLargeMessages;

    /**
     * writer property prefix for {@link software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider}<br/>
     */
    public static final String PROPERTY_AWS = "aws.";

    private String sequenceToken;

    private final ExecutorService cachedExecutor;
    private final ExecutorService singleExecutor;
    private static long lastTimestamp = 0;

    /**
     * @param properties Configuration for writer
     */
    public AwsCloudWatchLogsWriter(Map<String, String> properties) throws Exception
    {
        super(properties);

        logGroupName = getStringValue(PROPERTY_LOG_GROUP_NAME);

        if (logGroupName == null || logGroupName.isEmpty())
        {
            throw new Exception("parameter 'logGroupName' must be set in tinylog writer configuration");
        }

        streamName = getStringValue(PROPERTY_STREAM_NAME);

        if (streamName == null || streamName.isEmpty())
        {
            throw new Exception("parameter 'streamName' must be set in tinylog writer configuration");
        }

        splitLargeMessages = getBooleanValue("splitLargeMessages");

        properties.forEach((key, value) -> {
            if (key.startsWith(PROPERTY_AWS))
            {
                System.setProperty(key, value);
            }
        });

        // example
        // https://docs.aws.amazon.com/code-samples/latest/catalog/javav2-cloudwatch-src-main-java-com-example-cloudwatch-PutLogEvents.java.html
        logsClient = CloudWatchLogsClient.builder().credentialsProvider(DefaultCredentialsProvider.create()).build();

        try
        {
            DescribeLogGroupsRequest logGroupsRequest = DescribeLogGroupsRequest.builder().logGroupNamePrefix(logGroupName).build();
            DescribeLogGroupsResponse logGroupsResponse = logsClient.describeLogGroups(logGroupsRequest);

            if (logGroupsResponse.hasLogGroups())
            {
                if (!setSequenceToken())
                {
                    // log stream does not exist
                    // try to create it

                    CreateLogStreamRequest createLogStreamRequest = CreateLogStreamRequest.builder().logGroupName(logGroupName).logStreamName(streamName).build();
                    CreateLogStreamResponse createLogStreamResponse = logsClient.createLogStream(createLogStreamRequest);

                    if (createLogStreamResponse.sdkHttpResponse().isSuccessful())
                    {
                        if (!setSequenceToken())
                        {
                            String msg = String.format("log stream '%s' not found in log group name '%s'", streamName, logGroupName);
                            System.err.println(AwsCloudWatchLogsWriter.class.getSimpleName() + ": " + msg);
                            throw new Exception(msg);
                        }
                    }
                    else
                    {
                        String msg = String.format("log stream '%s' could not be created in log group name '%s'", streamName, logGroupName);
                        System.err.println(AwsCloudWatchLogsWriter.class.getSimpleName() + ": " + msg);
                        throw new Exception(msg);
                    }
                }
            }
            else
            {
                // log group does not exist
                String msg = String.format("log group '%s' not found", logGroupName);
                System.err.println(AwsCloudWatchLogsWriter.class.getSimpleName() + ": " + msg);
                throw new Exception(msg);
            }
        }
        catch (CloudWatchException e)
        {
            System.err.println(AwsCloudWatchLogsWriter.class.getSimpleName() + ": " + e + " - " + e.awsErrorDetails().errorMessage());
            throw e;
        }

        cachedExecutor = Executors.newCachedThreadPool();
        singleExecutor = Executors.newSingleThreadExecutor();
    }

    private boolean setSequenceToken() throws Exception
    {
        DescribeLogStreamsRequest logStreamRequest = DescribeLogStreamsRequest.builder().logGroupName(logGroupName)
                                                                                        .logStreamNamePrefix(streamName).build();
        DescribeLogStreamsResponse describeLogStreamsResponse = logsClient.describeLogStreams(logStreamRequest);

        if (describeLogStreamsResponse.logStreams() != null && describeLogStreamsResponse.logStreams().size() > 0)
        {
            // Assume that a single stream is returned since a specific stream name was specified in the previous request.
            LogStream logStream = describeLogStreamsResponse.logStreams().get(0);

            sequenceToken = logStream.uploadSequenceToken();

            return true;
        }

        return false;
    }

    @Override
    public void write(final LogEntry logEntry) throws Exception
    {
        cachedExecutor.execute(() -> {
            try
            {
                if (splitLargeMessages && logEntry.getMessage() != null && logEntry.getMessage().length() > MAX_MESSAGE_SIZE)
                {
                    List<LogEntry> logEntries = Util.splitLogEntries(logEntry);
                    final long ts = getTimestamp();

                    logEntries.forEach(e -> putLogEntry(e, ts));
                }
                else
                {
                    putLogEntry(logEntry, System.currentTimeMillis());
                }
            }
            catch (CloudWatchException e)
            {
                System.err.println(e.awsErrorDetails().errorMessage());
            }
        });
    }

    private synchronized static long getTimestamp()
    {
        long ts = System.currentTimeMillis();
        while (ts <= lastTimestamp)
        {
            ts++;
        }

        lastTimestamp = ts;

        return ts;
    }

    private void putLogEntry(LogEntry logEntry, long timestamp)
    {
        String msg = renderMessage(logEntry);

        // truncate message
        if (!splitLargeMessages && msg.length() > MAX_MESSAGE_SIZE)
        {
            msg = msg.substring(0, MAX_MESSAGE_SIZE) + String.format(MESSAGE_TRUNCATED, msg.length());
        }

        // Build an input log message to put to CloudWatch.
        InputLogEvent inputLogEvent = InputLogEvent.builder().message(msg).timestamp(timestamp).build();

        singleExecutor.execute(() -> {
            // Specify the request parameters.
            // Sequence token is required so that the log can be written to the
            // latest location in the stream.
            PutLogEventsRequest putLogEventsRequest = PutLogEventsRequest.builder().logEvents(Collections.singletonList(inputLogEvent))
                                                                                   .logGroupName(logGroupName).logStreamName(streamName)
                                                                                   .sequenceToken(sequenceToken).build();

            PutLogEventsResponse putLogEventsResponse = logsClient.putLogEvents(putLogEventsRequest);
            sequenceToken = putLogEventsResponse.nextSequenceToken();
        });
    }

    protected String renderMessage(LogEntry logEntry)
    {
        return render(logEntry);
    }

    @Override
    public void flush() throws Exception
    {

    }

    @Override
    public void close() throws Exception
    {
        singleExecutor.shutdown();
        cachedExecutor.shutdown();
        logsClient.close();
    }
}
