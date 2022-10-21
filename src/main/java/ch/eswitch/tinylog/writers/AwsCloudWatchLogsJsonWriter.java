package ch.eswitch.tinylog.writers;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.tinylog.Level;
import org.tinylog.core.LogEntry;
import org.tinylog.pattern.FormatPatternParser;
import org.tinylog.pattern.Token;
import org.tinylog.provider.InternalLogger;
import org.tinylog.writers.JsonWriter;

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

    private final Charset charset;
    private final Map<String, Token> fields;
    private final boolean lineDelimitedJson;

    /**
     * @param properties Configuration for writer
     */
    public AwsCloudWatchLogsJsonWriter(Map<String, String> properties) throws Exception
    {
        super(properties);

        String format = getStringValue("format");

        charset = getCharset();
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
            InternalLogger.log(Level.WARN, "Illegal format for JSON writer: " + format);
        }
    }

    /**
     * Prepares and adds a Json Object. Special characters will be escaped.
     *
     * @param logEntry LogEntry with information for token
     * @param builder Target for the created the JSON object
     */
    private void addJsonObject(final LogEntry logEntry, final StringBuilder builder)
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

        Map<String, Token> tokens = new HashMap<String, Token>();
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
}
