package ch.eswitch.tinylog.writers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.LoggerFactory;
import org.tinylog.Logger;
import org.tinylog.ThreadContext;
import org.tinylog.configuration.Configuration;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AWSCloudWatchLogsTest
{
    public static final String TAG_BIG_JSON = "bigJson";
    private static final org.junit.platform.commons.logging.Logger LOGGER = LoggerFactory.getLogger(AWSCloudWatchLogsTest.class);

    private boolean isAwsPropertySet(String awsKey)
    {
        String key = "writer_awscloudwatchlogs.aws." + awsKey;
        String value = Configuration.get(key);

        boolean isAwsPropertySet = !value.contains("xxxxxx") && !value.contains("${");
        // Assertions.assertTrue(isAwsPropertySet, key + " in tinylog configuration must be replaced by real value");

        if (!isAwsPropertySet)
        {
            LOGGER.error(() -> "TEST STOPPED!\nproperty '" + key + "' must be replaced by real value in tinylog configuration ");
        }

        return isAwsPropertySet;
    }

    @Test
    void log()
    {
        if (!isAwsPropertySet("accessKeyId")
                || !isAwsPropertySet("secretAccessKey"))
        {
            return;
        }

        long startTime = System.currentTimeMillis();

        // create log message
        String msgBase = "Hello AWS CloudWatch Logs! (" + startTime + ")";
        String msg1 = msgBase + " - 1";
        String msg2 = msgBase + " - 2";

        System.out.println("log message: " + msg1);

        // log to CloudWatch Logs
        Logger.info(msg1);

        ThreadContext.put("prefix", ":1:");
        Logger.debug(msg2);
        ThreadContext.clear();

        final String pattern1 = "1234567890 ";
        String msg3 = logBigMessage(":3:", pattern1);

        final String pattern2 = "9876543210 ";
        String msg4 = logBigMessage(":4:", pattern2);

        // wait until all is processed in AWS CloudWatch
        try
        {
            Thread.sleep(3000);
        }
        catch (InterruptedException ignored)
        {
        }

        boolean ok1 = false;
        boolean ok2 = false;
        boolean ok3 = false;
        boolean ok4 = false;
        boolean ok3truncated = false;
        boolean ok3splitted = false;

        String msg3truncated = AwsCloudWatchLogsWriter.MESSAGE_TRUNCATED.substring(0, 27);
        String msg3splitted = "[2/2]";

        String writerName = AwsCloudWatchLogsJsonWriter.getTaggedWriterName(TAG_BIG_JSON);
        Assertions.assertNotNull(writerName);
        LogGroupAndStreamName logGroupAndStreamName = AwsCloudWatchLogsJsonWriter.getLogGroupAndStreamName(writerName);

        Assertions.assertNotNull(logGroupAndStreamName);
        Assertions.assertNotNull(logGroupAndStreamName.logGroupName, AwsCloudWatchLogsWriter.PROPERTY_LOG_GROUP_NAME + " not found in tinylog config");
        Assertions.assertNotNull(logGroupAndStreamName.streamName, AwsCloudWatchLogsWriter.PROPERTY_STREAM_NAME + " not found in tinylog config");

        try
        {
            ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime),
                                                        ZoneId.systemDefault());
            String startDateTime = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            final List<OutputLogEvent> outputLogEvents = AwsCloudWatchLogsJsonWriter.getCombinedOutputLogEvents(writerName, startDateTime, null, null, null);

            int logLimit = outputLogEvents.size();

            System.out.println("outputLogEvents: " + logLimit);

            for (int c = 0; c < logLimit; c++)
            {
                OutputLogEvent e = outputLogEvents.get(c);

                final String message = e.message();
                if (message.contains(msg1))
                {
                    ok1 = true;
                }
                else if (message.contains(msg2))
                {
                    ok2 = true;
                }
                else if (message.contains(msg3truncated) && message.contains(pattern1))
                {
                    ok3truncated = true;
                }
                else if (message.contains(msg3splitted) && message.contains(pattern1))
                {
                    ok3splitted = true;
                }

                System.out.println(message);
            }

            System.out.println("Successfully got CloudWatch log events!");

            List<OutputLogEvent> combinedOutputLogEvents = Util.combineOutputLogEvents(outputLogEvents);

            System.out.println("combinedOutputLogEvents: " + combinedOutputLogEvents.size());

            Assertions.assertNotNull(combinedOutputLogEvents, "combinedOutputLogEvents are missing");

            Assertions.assertTrue(combinedOutputLogEvents.size() >= 2, "combined outputLogEvents");

            for (OutputLogEvent e : combinedOutputLogEvents)
            {
                final String message = e.message();
                if (message.contains(msg3) && message.contains("[1]"))
                {
                    ok3 = true;
                }
                else if (message.contains(msg4) && message.contains("[1]"))
                {
                    ok4 = true;
                }
            }

            Assertions.assertTrue(ok3, "original msg3 not found in combinedOutputLogEvents");
            Assertions.assertTrue(ok4, "original msg4 not found in combinedOutputLogEvents");
        }
        catch (CloudWatchException e)
        {
            System.err.println(e.awsErrorDetails()
                                .errorMessage());
        }

        Assertions.assertTrue(ok1, "Log message '" + msg1 + "' not found");
        Assertions.assertTrue(ok2, "Log message '" + msg2 + "' not found");
        Assertions.assertTrue(ok3truncated, "Truncated Log message '" + msg3truncated + "' not found");
        Assertions.assertFalse(ok3splitted, "Splitted Log message '" + msg3splitted + "' found (should be replaced by truncated message)");
    }

    /**
     * create big string (more than {@value AwsCloudWatchLogsWriter#MAX_MESSAGE_SIZE})
     *
     * @param pattern text pattern to create big string
     * @return big string
     */
    private String createBigString(String pattern)
    {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < AwsCloudWatchLogsWriter.MAX_MESSAGE_SIZE + 10 * 1024)
        {
            sb.append(pattern);
        }

        final String endOfMessage = " - end of message";
        sb.append(endOfMessage);

        return sb.toString();
    }

    private String logBigMessage(String context, String pattern)
    {
        ThreadContext.put("prefix", context);

        String msg = createBigString(pattern);

        Logger.tag(TAG_BIG_JSON).trace(msg);
        ThreadContext.clear();

        return msg;
    }

}
