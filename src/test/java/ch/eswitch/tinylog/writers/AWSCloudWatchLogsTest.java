package ch.eswitch.tinylog.writers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.LoggerFactory;
import org.tinylog.Logger;
import org.tinylog.configuration.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;

public class AWSCloudWatchLogsTest
{
    private static org.junit.platform.commons.logging.Logger LOGGER = LoggerFactory.getLogger(AWSCloudWatchLogsTest.class);

    private boolean isAwsPropertySet(String awsKey)
    {
        String key = "writer_awscloudwatchlogs.aws." + awsKey;
        String value = Configuration.get(key);

        boolean isAwsPropertySet = !value.contains("xxxxxx");
        // Assertions.assertTrue(isAwsPropertySet, key + " in tinylog configuration must be replaced by real value");

        if (!isAwsPropertySet)
            LOGGER.error(() -> "TEST STOPPED!\nproperty '" + key + "' must be replaced by real value in tinylog configuration ");

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
        String msg = "Hello AWS CloudWatch Logs! (" + startTime + ")";
        System.out.println("log message: " + msg);

        // log to CloudWatch Logs
        Logger.info(msg);

        // wait until all is processed in AWS CloudWatch
        try
        {
            Thread.sleep(3000);
        } catch (InterruptedException e)
        {
        }

        // read logs from CloudWatch Logs
        // example
        // https://docs.aws.amazon.com/code-samples/latest/catalog/javav2-cloudwatch-src-main-java-com-example-cloudwatch-GetLogEvents.java.html
        CloudWatchLogsClient logsClient = CloudWatchLogsClient.builder()
                                                              .credentialsProvider(DefaultCredentialsProvider.create())
                                                              .build();

        boolean ok = false;

        try
        {
            GetLogEventsRequest getLogEventsRequest = GetLogEventsRequest.builder()
                                                                         .logGroupName("eswitch")
                                                                         .logStreamName("xrb")
                                                                         .startFromHead(true)
                                                                         .startTime(startTime)
                                                                         .build();

            int logLimit = logsClient.getLogEvents(getLogEventsRequest)
                                     .events()
                                     .size();

            System.out.println("logLimit: " + logLimit);

            for (int c = 0; c < logLimit; c++)
            {
                OutputLogEvent e = logsClient.getLogEvents(getLogEventsRequest)
                                             .events()
                                             .get(c);

                if (e.message()
                     .contains(msg))
                {
                    ok = true;
                }

                System.out.println(e.message());
            }

            System.out.println("Successfully got CloudWatch log events!");

        } catch (CloudWatchException e)
        {
            System.err.println(e.awsErrorDetails()
                                .errorMessage());
        }

        Assertions.assertTrue(ok, "Log message '" + msg + "' not found");
    }

}
