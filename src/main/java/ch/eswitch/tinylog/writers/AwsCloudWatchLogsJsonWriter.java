package ch.eswitch.tinylog.writers;

import org.tinylog.Level;
import org.tinylog.configuration.Configuration;
import org.tinylog.core.LogEntry;
import org.tinylog.pattern.FormatPatternParser;
import org.tinylog.pattern.Token;
import org.tinylog.writers.JsonWriter;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.paginators.GetLogEventsIterable;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * tinylog 2 AWS CloudWatch Logs JSON Writer based on Amazon SDK for Java 2.x<br/>
 * This is a <a href="https://tinylog.org/v2/extending/#custom-writer">custom writer</a> for
 * <a href="https://tinylog.org/v2/">tinylog 2</a> logging framework to send log events to AWS CloudWatch Logs.<br/>
 * Message is rendered to JSON format based on {@link JsonWriter}<br/>
 * see <a href="https://tinylog.org/v2/configuration/#json-writer">JSON Writer</a> for JSON configuration<br/>
 * Properties {@code file}, {@code charset}, {@code append} and {@code buffered} are NOT supported<br/>
 * <br/>
 * see {@link AwsCloudWatchLogsWriter} for AWS configuration
 *
 * @author Martin Schelldorfer, 2022
 */
public class AwsCloudWatchLogsJsonWriter extends AwsCloudWatchLogsWriter
{
    private static final String NEW_LINE = System.getProperty("line.separator");
    private static final String FIELD_PREFIX = "field.";
    public static final String WRITER_PREFIX = "writer_";

    private final Map<String, Token> fields;
    private final boolean lineDelimitedJson;

    /**
     * @param properties Configuration for writer
     */
    public AwsCloudWatchLogsJsonWriter(Map<String, String> properties) throws Exception
    {
        super(properties);

        String format = getStringValue("format");

        fields = createTokens(properties);

        if (format == null
                || "JSON".equalsIgnoreCase(format))
        {
            lineDelimitedJson = false;
        }
        else if ("LDJSON".equalsIgnoreCase(format))
        {
            lineDelimitedJson = true;
        }
        else
        {
            lineDelimitedJson = false;
            Util.log(Level.WARN, "Illegal format for JSON writer: %s", format);
        }
    }

    /**
     * Prepares and adds a Json Object. Special characters will be escaped.
     *
     * @param logEntry LogEntry with information for token
     * @param builder Target for the created the JSON object
     */
    private void addJsonObject(LogEntry logEntry, final StringBuilder builder)
    {
        if (!lineDelimitedJson)
        {
            builder.append(NEW_LINE);
            builder.append('\t');
        }

        builder.append("{");

        if (!lineDelimitedJson)
        {
            builder.append(NEW_LINE);
        }

        // make sure logEntry has context
        if(logEntry.getContext() == null)
            logEntry = Util.copyLogEntry(logEntry, logEntry.getMessage(), -1, -1);

        Token[] tokenEntries = fields.values()
                .toArray(new Token[0]);
        String[] fields = this.fields.keySet()
                .toArray(new String[0]);

        for (int i = 0; i < tokenEntries.length; i++)
        {
            if (!lineDelimitedJson)
            {
                builder.append("\t\t");
            }

            builder.append('\"');
            builder.append(fields[i]);
            builder.append("\": \"");
            int start = builder.length();

            Token token = tokenEntries[i];
            token.render(logEntry, builder);

            escapeCharacter("\\", "\\\\", builder, start);
            escapeCharacter("\"", "\\\"", builder, start);
            escapeCharacter(NEW_LINE, "\\n", builder, start);
            escapeCharacter("\t", "\\t", builder, start);
            escapeCharacter("\b", "\\b", builder, start);
            escapeCharacter("\f", "\\f", builder, start);
            escapeCharacter("\n", "\\n", builder, start);
            escapeCharacter("\r", "\\r", builder, start);

            builder.append('"');

            if (i + 1 < this.fields.size())
            {
                builder.append(",");

                if (lineDelimitedJson)
                {
                    builder.append(' ');
                }
                else
                {
                    builder.append(NEW_LINE);
                }
            }
        }

        if (!lineDelimitedJson)
        {
            builder.append(NEW_LINE)
                   .append('\t');
        }

        builder.append('}');

        if (lineDelimitedJson)
        {
            builder.append(NEW_LINE);
        }
    }

    /**
     * Replaces a character by its replacement everywhere in a string builder, starting at the given index.
     *
     * @param character The character to replace
     * @param replacement The replacement for the given character
     * @param builder The string builder to change
     * @param startIndex The index in the string builder to start at
     */
    private void escapeCharacter(final String character, final String replacement, final StringBuilder builder, final int startIndex)
    {
        for (int index = builder.indexOf(character, startIndex); index != -1; index = builder.indexOf(character, index + replacement.length()))
        {
            builder.replace(index, index + character.length(), replacement);
        }
    }

    @Override
    protected final String renderMessage(final LogEntry logEntry)
    {
        StringBuilder builder = new StringBuilder();
        addJsonObject(logEntry, builder);

        return builder.toString();
    }

    /**
     * Creates the token for all fields.
     *
     * @param properties The configuration for the {@link JsonWriter}
     * @return All field names mapped to their tokens
     */
    private static Map<String, Token> createTokens(final Map<String, String> properties)
    {
        FormatPatternParser parser = new FormatPatternParser(properties.get("exception"));

        Map<String, Token> tokens = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet())
        {
            if (entry.getKey()
                     .toLowerCase(Locale.ROOT)
                     .startsWith(FIELD_PREFIX))
            {
                tokens.put(entry.getKey()
                                .substring(FIELD_PREFIX.length()), parser.parse(entry.getValue()));
            }
        }
        return tokens;
    }

    /**
     * get all configured writer names from tinylog config for this {@link AwsCloudWatchLogsJsonWriter}
     *
     * @return writer names
     */
    public static List<String> getAllWriterNames()
    {
        Map<String, String> writerConfig = Configuration.getSiblings(WRITER_PREFIX);
        Util.log(Level.DEBUG, "%s - writerConfig size: %d", AwsCloudWatchLogsJsonWriter.class.getSimpleName(), writerConfig.keySet().size());
        if (writerConfig != null)
        {
            Util.log(Level.DEBUG, "%s - writer name: %s", AwsCloudWatchLogsJsonWriter.class.getSimpleName(), getWriterName());
            List<String> writerNames = writerConfig.entrySet()
                    .stream()
                    .filter(w -> w.getValue().equalsIgnoreCase(getWriterName()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            Util.log(Level.DEBUG, "%s - writerNames size: %d", AwsCloudWatchLogsJsonWriter.class.getSimpleName(), writerNames.size());
            return writerNames;
        }

        return null;
    }

    /**
     * get tinylog writer name for a tagged writer
     *
     * @param tagName tag name in tinylog configuration
     * @return writer name
     */
    public static String getTaggedWriterName(String tagName)
    {
        Configuration.getChildren(WRITER_PREFIX);

        Map<String, String> writerConfig = Configuration.getSiblings(WRITER_PREFIX);

        return writerConfig.entrySet()
                .stream()
                .filter(wn -> Configuration.getChildren(wn.getKey()).entrySet()
                        .stream()
                        .anyMatch(w -> (w.getKey().equals("tag") && w.getValue().equals(tagName))))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public static LogGroupAndStreamName getLogGroupAndStreamName(String writerName)
    {
        Map<String, String> writerConfig = AwsCloudWatchLogsJsonWriter.getWriterConfig(writerName);
        if (writerConfig != null)
        {
            String logGroupName = writerConfig.get(AwsCloudWatchLogsWriter.PROPERTY_LOG_GROUP_NAME);
            String streamName = writerConfig.get(AwsCloudWatchLogsWriter.PROPERTY_STREAM_NAME);

            if ((logGroupName != null && !logGroupName.isEmpty()) || (streamName != null && !streamName.isEmpty()))
            {
                return new LogGroupAndStreamName(logGroupName, streamName);
            }
        }

        return null;
    }

    public static void setAwsSystemProperties(String writerName)
    {
        Map<String, String> writerConfig = AwsCloudWatchLogsJsonWriter.getWriterConfig(writerName);
        if (writerConfig != null)
        {
            writerConfig.entrySet()
                    .stream()
                    .filter(c -> c.getKey().startsWith(PROPERTY_AWS))
                    .forEach(c -> System.setProperty(c.getKey(), c.getValue()));
        }
    }

    /**
     * get tinylog config of logger by writer name
     *
     * @param writerName writer name
     * @return config
     */
    public static Map<String, String> getWriterConfig(String writerName)
    {
        return Configuration.getChildren(writerName);
    }

    private static boolean isTaggedWriter(String writerName)
    {
        Map<String, String> cc = Configuration.getChildren(writerName);
        String tagValue = cc.get("tag");
        return tagValue != null && !tagValue.isEmpty();
    }

    /**
     * get name for tinylog configuration of this writer
     *
     * @return writer name
     */
    private static String getWriterName()
    {
        StringBuilder writerName = new StringBuilder(AwsCloudWatchLogsJsonWriter.class.getSimpleName());
        int posUpperCaseCharacter = Util.lastIndexOfUperCaseCharacter(writerName);

        // remove 'Writer' suffix from class name
        if (posUpperCaseCharacter > 0)
        {
            writerName.delete(posUpperCaseCharacter, writerName.length());
        }

        // insert 'space' after each upper case character
        for (int i = writerName.length() - 1; i > 0; i--)
        {
            if (Character.isUpperCase(writerName.charAt(i)))
            {
                writerName.insert(i, " ");
            }
        }

        return writerName.toString().toLowerCase();
    }

    /**
     * get all combined Output Log Events from AWS CloudWatch for a specific writer name
     *
     * @param writer tinylog writer name
     * @param request (optional) search parameters to filer for Log Events
     * @return combined Output Log Events
     */
    public static List<OutputLogEvent> getCombinedOutputLogEvents(String writer, HttpServletRequest request)
    {
        setAwsSystemProperties(writer);

        CloudWatchLogsClient logsClient = CloudWatchLogsClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        LogGroupAndStreamName logGroupAndStreamName = AwsCloudWatchLogsJsonWriter.getLogGroupAndStreamName(
                writer);

        if (logGroupAndStreamName != null)
        {
            GetLogEventsRequest.Builder builder = GetLogEventsRequest.builder()
                    .logGroupName(logGroupAndStreamName.logGroupName)
                    .logStreamName(logGroupAndStreamName.streamName).startFromHead(true)
                    .limit(1000);

            // set start and end date/time and other search parameters
            String startDateTime = null;
            String endDateTime = null;
            String searchTerm = null;
            String useRegExp = null;
            if (request != null)
            {
                String timeRadios = request.getParameter("timeRadios");
                if (timeRadios != null)
                {
                    if (timeRadios.equals("absoluteRadio"))
                    {
                        startDateTime = request.getParameter("startDateTime");
                        endDateTime = request.getParameter("endDateTime");
                    }
                    else if (timeRadios.equals("relativeRadio"))
                    {
                        String relativeTime = request.getParameter("relativeTime");
                        String relativeUnit = request.getParameter("relativeUnit");

                        if (relativeTime != null && relativeTime.length() > 0 && relativeUnit != null && relativeUnit.length() > 0)
                        {
                            int min = Integer.parseInt(relativeTime);
                            switch (relativeUnit)
                            {
                                case "min":
                                    // nothing to do
                                    break;
                                case "h":
                                    min = min * 60;
                                    break;
                                case "d":
                                    min = min * 60 * 24;
                                    break;
                            }

                            ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.now(),
                                                                        ZoneId.systemDefault());

                            zdt = zdt.plusMinutes(-1 * min);
                            startDateTime = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        }
                    }
                }

                request.getParameterMap();

                searchTerm = request.getParameter("searchTerm");
                useRegExp = request.getParameter("useRegExp");
            }

            if (startDateTime == null)
            {
                ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.now(),
                                                            ZoneId.systemDefault());
                startDateTime = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }

            setTime(builder, startDateTime, true);
            setTime(builder, endDateTime, false);

            GetLogEventsRequest logEventsRequest = builder.build();

            GetLogEventsIterable responses = logsClient.getLogEventsPaginator(logEventsRequest);
            final List<OutputLogEvent> outputLogEvents = new ArrayList<>();
            responses.stream()
                    .forEach(responsePage -> {
                        if (responsePage.hasEvents())
                        {
                            outputLogEvents.addAll(responsePage.events());
                            int logEvents = responsePage.events().size();

                            Util.log(Level.DEBUG, "log events: %d", logEvents);
                        }
                    });

            DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now());

            Util.log(Level.DEBUG, "outputLogEvents: %d", outputLogEvents.size());

            List<OutputLogEvent> combinedOutputLogEvents = Util.combineOutputLogEvents(
                    outputLogEvents);

            Util.log(Level.DEBUG, "combinedOutputLogEvents: %d", combinedOutputLogEvents.size());

            // filter log events by search term
            if (searchTerm != null && !searchTerm.isEmpty())
            {
                final String searchTermFinal = searchTerm.toUpperCase();

                final boolean regExp = Boolean.parseBoolean(useRegExp);
                Util.log(Level.DEBUG, "filter with regExp: %b, searchTerm: %s", regExp, searchTerm);

                final Pattern pattern;
                if (regExp)
                {
                    pattern = Pattern.compile(searchTerm);
                }
                else
                {
                    pattern = null;
                }

                combinedOutputLogEvents = combinedOutputLogEvents.stream()
                        .filter(e -> (!regExp && e.message().toUpperCase()
                                                  .contains(searchTermFinal)) || (regExp && pattern.matcher(
                                e.message()).matches()))
                        .collect(Collectors.toList());

                Util.log(Level.DEBUG, "filtered combinedOutputLogEvents: %d", combinedOutputLogEvents.size());
            }

            return combinedOutputLogEvents;
        }

        return null;
    }

    private static void setTime(GetLogEventsRequest.Builder builder, String dateTime,
                                boolean startTime)
    {
        if (dateTime != null && !dateTime.isEmpty())
        {
            LocalDateTime ldt = LocalDateTime.parse(dateTime,
                                                    DateTimeFormatter.ISO_DATE_TIME);
            long millis = ldt.atZone(ZoneId.systemDefault()).toInstant()
                             .toEpochMilli();
            if (startTime)
            {
                builder.startTime(millis);
            }
            else
            {
                builder.endTime(millis);
            }
        }
    }
}
