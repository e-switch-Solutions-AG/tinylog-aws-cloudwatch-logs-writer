package ch.eswitch.tinylog.writers;

public class LogGroupAndStreamName
{
    public final String logGroupName;
    public final String streamName;

    public LogGroupAndStreamName(String logGroupName, String streamName)
    {
        this.logGroupName = logGroupName;
        this.streamName = streamName;
    }
}
