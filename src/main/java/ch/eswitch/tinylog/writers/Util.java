package ch.eswitch.tinylog.writers;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import org.tinylog.Level;
import org.tinylog.core.LogEntry;
import org.tinylog.provider.InternalLogger;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;

import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Util
{

    private static final String PARTIAL_MESSAGE_PATTERN_REGEX = "\\[(\\d*)/(\\d*)\\]";

    private static final Pattern PARTIAL_MESSAGE_PATTERN = Pattern.compile(PARTIAL_MESSAGE_PATTERN_REGEX);
    private static final String[] JSON_ATTRIBUTE_NAMES_MESSAGE = new String[] { AwsCloudWatchLogsWriter.JSON_MESSAGE_ATTRIBUTE };
    private static LocalDateTime lastLogMessage;

    /**
     * split long entry with large message into log entries with partial message<br/>
     *
     * @param logEntry log entry
     * @return list of splitted log entries
     */
    public static List<LogEntry> splitLogEntries(LogEntry logEntry)
    {
        final String msg = logEntry.getMessage();

        final int totalParts = (int) Math.ceil((double) msg.length() / (double) AwsCloudWatchLogsWriter.MAX_MESSAGE_SIZE);

        ArrayList<LogEntry> logEntries = new ArrayList<>(totalParts);
        int start = 0;
        for (int part = 0; part < totalParts; part++)
        {
            int end = Math.min(msg.length(), start + AwsCloudWatchLogsWriter.MAX_MESSAGE_SIZE);
            String msgPart = msg.substring(start, end);
            start = end;

            logEntries.add(copyLogEntry(logEntry, msgPart, part, totalParts));
        }

        return logEntries;
    }

    /**
     * create a new log entry based on existing log entry<br/>
     * context with key {@value AwsCloudWatchLogsWriter#CONTEXT_KEY_PART} is added<br/>
     * {@value AwsCloudWatchLogsWriter#CONTEXT_KEY_PART} value is formatted as <code>[&lt;part&gt;/&lt;total parts&gt;]</code>
     *
     * @param logEntry original log entry
     * @param msgPart part of message
     * @param part current part
     * @param totalParts total number of parts
     * @return copied log entry
     */
    static LogEntry copyLogEntry(LogEntry logEntry, String msgPart, int part, int totalParts)
    {
        Map<String, String> context = new HashMap<>();
        if(logEntry.getContext() != null)
            context.putAll(logEntry.getContext());

        if(part >= 0 && totalParts >= 0)
            context.put(AwsCloudWatchLogsWriter.CONTEXT_KEY_PART, String.format(AwsCloudWatchLogsWriter.CONTEXT_PART_FORMAT, part + 1, totalParts));

        return new LogEntry(logEntry.getTimestamp(),
                            logEntry.getThread(),
                            context,
                            logEntry.getClassName(),
                            logEntry.getMethodName(),
                            logEntry.getFileName(),
                            logEntry.getLineNumber(),
                            logEntry.getTag(),
                            logEntry.getLevel(),
                            msgPart,
                            logEntry.getException());
    }

    public static List<OutputLogEvent> combineOutputLogEvents(List<OutputLogEvent> outputLogEvents)
    {
        log(Level.DEBUG, "outputLogEvents: %d", outputLogEvents.size());
        // get all partial messages grouped by timestamp
        Map<Long, List<OutputLogEvent>> groupedOutputLogEvents = outputLogEvents.stream()
                .filter(e -> PARTIAL_MESSAGE_PATTERN.matcher(e.message()).find())
                .collect(Collectors.groupingBy(OutputLogEvent::timestamp));

        log(Level.DEBUG, "groupedOutputLogEvents: %d", groupedOutputLogEvents.size());

        List<OutputLogEvent> combinedOutputLogEvents = new ArrayList<>(outputLogEvents.size());
        combinedOutputLogEvents.addAll(outputLogEvents);

        if (!groupedOutputLogEvents.isEmpty())
        {
            groupedOutputLogEvents.values()
                    .forEach(combinedOutputLogEvents::removeAll);

            log(Level.TRACE, "groupedOutputLogEvents.forEach");

            groupedOutputLogEvents.values()
                    .forEach(partialEvents -> {

                        log(Level.TRACE, "partialEvents: %d", partialEvents.size());
                        // create map with message part index as key
                        Map<Integer, OutputLogEvent> messagePartIndexList = partialEvents.stream()
                                .collect(
                                        Collectors.toMap(Util::getMessagePartIndex, e -> e));

                        log(Level.TRACE, "messagePartIndexList: %d", messagePartIndexList.size());

                        if (!messagePartIndexList.isEmpty())
                        {
                            OutputLogEvent firstEntry = messagePartIndexList.get(1);

                            boolean entryAdded = false;
                            if (firstEntry != null)
                            {
                                int totalParts = getMessagePartTotal(firstEntry.message());
                                if (totalParts > 0)
                                {
                                    if (totalParts == messagePartIndexList.size())
                                    {
                                        // sort list
                                        List<OutputLogEvent> sortedValues = messagePartIndexList.entrySet()
                                                .stream()
                                                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                                                .map(Map.Entry::getValue)
                                                .collect(Collectors.toList());

                                        //log(Level.TRACE, "messagePartIndexList sorted");

                                        // all partial messages found
                                        log(Level.TRACE, "create full message");
                                        String fullMessage = sortedValues
                                                .stream()
                                                .map(e -> getJsonMessageAttributeValue(e.message()))
                                                .collect(Collectors.joining());

                                        log(Level.TRACE, "full message created");

                                        StringBuilder jsonMessage = new StringBuilder();
                                        jsonMessage.append(firstEntry.message());

                                        String[] jsonAttributeValues = getJsonAttributeValues(jsonMessage.toString(),
                                                                                              new String[] {
                                                                                                      AwsCloudWatchLogsWriter.JSON_MESSAGE_ATTRIBUTE,
                                                                                                      AwsCloudWatchLogsWriter.JSON_CONTEXT_ATTRIBUTE });

                                        log(Level.TRACE, "JSON attribute values read");
                                        if (jsonAttributeValues != null)
                                        {
                                            String originalMessage = jsonAttributeValues[0];
                                            String originalContext = jsonAttributeValues[1];

                                            // replace json message attribute
                                            replace(jsonMessage, originalMessage, fullMessage);

                                            //log(Level.TRACE, "message replaced");
                                            // replace json context attribute - [1/2] by [1]
                                            int posEndPart = originalContext.indexOf(AwsCloudWatchLogsWriter.CONTEXT_PART_FORMAT.substring(AwsCloudWatchLogsWriter.CONTEXT_PART_FORMAT.length() - 1));
                                            if (posEndPart > 0)
                                            {
                                                //log(Level.TRACE, "replace context");
                                                String newContext = AwsCloudWatchLogsWriter.CONTEXT_PART_FORMAT.charAt(0) + "1" + originalContext.substring(posEndPart);

                                                replace(jsonMessage, originalContext, newContext);
                                            }

                                            //log(Level.TRACE, "copy entry");
                                            OutputLogEvent newEntry = firstEntry.toBuilder().message(jsonMessage.toString()).build();
                                            //log(Level.TRACE, "new entry created");

                                            combinedOutputLogEvents.add(newEntry);
                                            entryAdded = true;

                                            log(Level.TRACE, "entry added");
                                        }
                                    }
                                }
                            }

                            if (!entryAdded)
                            {
                                combinedOutputLogEvents.addAll(messagePartIndexList.values());
                            }
                        }
                    });
        }

        // sort reversed by timestamp
        combinedOutputLogEvents.sort(Comparator.comparing(c -> (c.timestamp() * -1)));
        return combinedOutputLogEvents;
    }

    private static void replace(StringBuilder text, String originalText, String replacementText)
    {
        int posStart = text.indexOf(originalText);
        text.replace(posStart, posStart + originalText.length(), replacementText);
    }

    private static String getJsonMessageAttributeValue(final String message)
    {
        String[] jsonAttributeValues = getJsonAttributeValues(message, JSON_ATTRIBUTE_NAMES_MESSAGE);
        if (jsonAttributeValues != null)
        {
            return jsonAttributeValues[0];
        }

        return null;
    }

    /**
     * get json attributes values from JSON object for specified attribute names
     *
     * @param message complete JSON message
     * @param jsonAttributeNames list of JSON attribute names
     * @return values from JSON attributes
     */
    private static String[] getJsonAttributeValues(final String message, final String[] jsonAttributeNames)
    {
        JsonReader reader = Json.createReader(new StringReader(message));
        if (reader != null)
        {
            JsonObject jsonObject = reader.readObject();
            if (jsonObject != null)
            {
                String[] jsonValues = new String[jsonAttributeNames.length];
                for (int i = 0; i < jsonAttributeNames.length; i++)
                {
                    JsonString jsonString = jsonObject.getJsonString(jsonAttributeNames[i]);
                    if (jsonString != null)
                    {
                        jsonValues[i] = jsonString.getString();
                    }
                }

                return jsonValues;
            }
        }

        return null;
    }

    private static int getMessagePart(String message, int group)
    {
        Matcher matcher = PARTIAL_MESSAGE_PATTERN.matcher(message);
        if (matcher.find() && group <= matcher.groupCount())
        {
            String part = matcher.group(group);
            try
            {
                return Integer.parseInt(part);
            }
            catch (NumberFormatException ignored)
            {
            }
        }
        return -1;
    }

    static int getMessagePartIndex(OutputLogEvent event)
    {
        return getMessagePartIndex(event.message());
    }

    static int getMessagePartIndex(String message)
    {
        return getMessagePart(message, 1);
    }

    static int getMessagePartTotal(String message)
    {
        return getMessagePart(message, 2);
    }

    public static int lastIndexOfUperCaseCharacter(StringBuilder str)
    {
        for (int i = str.length() - 1; i >= 0; i--)
        {
            if (Character.isUpperCase(str.charAt(i)))
            {
                return i;
            }
        }
        return -1;
    }

    public static void log(Level level, String message, Object... args)
    {
        if (args != null && args.length > 0)
        {
            message = String.format(message, args);
        }

        StringBuilder difference = new StringBuilder();
        if (lastLogMessage != null)
        {
            LocalDateTime now = LocalDateTime.now();
            long diff = ChronoUnit.MILLIS.between(lastLogMessage, now);
            difference = new StringBuilder(String.format(" (%dms)", diff));
            while (difference.length() < 9)
            {
                difference.insert(0, " ");
            }
            lastLogMessage = now;
        }
        else
        {
            lastLogMessage = LocalDateTime.now();
        }

        StringBuilder sTimestamp = new StringBuilder();
        while (sTimestamp.length() < 23)
        {
            sTimestamp.append(" ");
        }

        InternalLogger.log(level, getFormattedTimestamp(lastLogMessage) + difference + " " + message);

    }

    public static String getFormattedTimestamp(long millis)
    {
        return getFormattedTimestamp(Instant.ofEpochMilli(millis)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime());
    }

    public static String getFormattedTimestamp(LocalDateTime dateTime)
    {
        StringBuilder sTimestamp = new StringBuilder(DateTimeFormatter.ISO_DATE_TIME.format(dateTime));
        while (sTimestamp.length() < 23)
        {
            sTimestamp.append(" ");
        }

        return sTimestamp.toString();
    }
}
