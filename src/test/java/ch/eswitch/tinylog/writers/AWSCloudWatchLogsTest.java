package ch.eswitch.tinylog.writers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.LoggerFactory;
import org.tinylog.Logger;
import org.tinylog.ThreadContext;
import org.tinylog.configuration.Configuration;
import software.amazon.awssdk.services.cloudwatch.model.CloudWatchException;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

            final List<OutputLogEvent> outputLogEvents = AwsCloudWatchLogsJsonWriter.getCombinedOutputLogEvents(writerName, new TestServletRequest(startDateTime));

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

class TestServletRequest implements HttpServletRequest
{
    final String startDateTime;

    public TestServletRequest(String startDateTime)
    {
        this.startDateTime = startDateTime;
    }

    @Override
    public String getAuthType()
    {
        return null;
    }

    @Override
    public Cookie[] getCookies()
    {
        return new Cookie[0];
    }

    @Override
    public long getDateHeader(String name)
    {
        return 0;
    }

    @Override
    public String getHeader(String name)
    {
        return null;
    }

    @Override
    public Enumeration<String> getHeaders(String name)
    {
        return null;
    }

    @Override
    public Enumeration<String> getHeaderNames()
    {
        return null;
    }

    @Override
    public int getIntHeader(String name)
    {
        return 0;
    }

    @Override
    public String getMethod()
    {
        return null;
    }

    @Override
    public String getPathInfo()
    {
        return null;
    }

    @Override
    public String getPathTranslated()
    {
        return null;
    }

    @Override
    public String getContextPath()
    {
        return null;
    }

    @Override
    public String getQueryString()
    {
        return null;
    }

    @Override
    public String getRemoteUser()
    {
        return null;
    }

    @Override
    public boolean isUserInRole(String role)
    {
        return false;
    }

    @Override
    public Principal getUserPrincipal()
    {
        return null;
    }

    @Override
    public String getRequestedSessionId()
    {
        return null;
    }

    @Override
    public String getRequestURI()
    {
        return null;
    }

    @Override
    public StringBuffer getRequestURL()
    {
        return null;
    }

    @Override
    public String getServletPath()
    {
        return null;
    }

    @Override
    public HttpSession getSession(boolean create)
    {
        return null;
    }

    @Override
    public HttpSession getSession()
    {
        return null;
    }

    @Override
    public String changeSessionId()
    {
        return null;
    }

    @Override
    public boolean isRequestedSessionIdValid()
    {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie()
    {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL()
    {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl()
    {
        return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException
    {
        return false;
    }

    @Override
    public void login(String username, String password) throws ServletException
    {

    }

    @Override
    public void logout() throws ServletException
    {

    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException
    {
        return null;
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException
    {
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> httpUpgradeHandlerClass) throws IOException, ServletException
    {
        return null;
    }

    @Override
    public Object getAttribute(String name)
    {
        return null;
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        return null;
    }

    @Override
    public String getCharacterEncoding()
    {
        return null;
    }

    @Override
    public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException
    {

    }

    @Override
    public int getContentLength()
    {
        return 0;
    }

    @Override
    public long getContentLengthLong()
    {
        return 0;
    }

    @Override
    public String getContentType()
    {
        return null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException
    {
        return null;
    }

    @Override
    public String getParameter(String name)
    {
        if (name.equals("timeRadios"))
        {
            return "absoluteRadio";
        }

        if (name.equals("startDateTime"))
        {
            return startDateTime;
        }

        return null;
    }

    @Override
    public Enumeration<String> getParameterNames()
    {
        return null;
    }

    @Override
    public String[] getParameterValues(String name)
    {
        return new String[0];
    }

    @Override
    public Map<String, String[]> getParameterMap()
    {
        return null;
    }

    @Override
    public String getProtocol()
    {
        return null;
    }

    @Override
    public String getScheme()
    {
        return null;
    }

    @Override
    public String getServerName()
    {
        return null;
    }

    @Override
    public int getServerPort()
    {
        return 0;
    }

    @Override
    public BufferedReader getReader() throws IOException
    {
        return null;
    }

    @Override
    public String getRemoteAddr()
    {
        return null;
    }

    @Override
    public String getRemoteHost()
    {
        return null;
    }

    @Override
    public void setAttribute(String name, Object o)
    {

    }

    @Override
    public void removeAttribute(String name)
    {

    }

    @Override
    public Locale getLocale()
    {
        return null;
    }

    @Override
    public Enumeration<Locale> getLocales()
    {
        return null;
    }

    @Override
    public boolean isSecure()
    {
        return false;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path)
    {
        return null;
    }

    @Override
    public String getRealPath(String path)
    {
        return null;
    }

    @Override
    public int getRemotePort()
    {
        return 0;
    }

    @Override
    public String getLocalName()
    {
        return null;
    }

    @Override
    public String getLocalAddr()
    {
        return null;
    }

    @Override
    public int getLocalPort()
    {
        return 0;
    }

    @Override
    public ServletContext getServletContext()
    {
        return null;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException
    {
        return null;
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException
    {
        return null;
    }

    @Override
    public boolean isAsyncStarted()
    {
        return false;
    }

    @Override
    public boolean isAsyncSupported()
    {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext()
    {
        return null;
    }

    @Override
    public DispatcherType getDispatcherType()
    {
        return null;
    }
}
