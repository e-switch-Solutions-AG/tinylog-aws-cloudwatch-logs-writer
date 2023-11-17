package ch.eswitch.tinylog.writers;

import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@WebServlet(
        urlPatterns = { "/awslogviewer" }
)
public class AwsCloudWatchLogsViewerServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        String nextJSP = "/jsp/list-awsstreams.jsp";
        RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(nextJSP);
        List<String> writers = AwsCloudWatchLogsJsonWriter.getAllWriterNames();
        req.setAttribute("writerList", writers);

        List<String> selectedWriters = getSelectedWriters(req.getParameterMap());

        String outputType = req.getParameter("outputType");
        if (outputType != null && outputType.equals("file") && selectedWriters != null && selectedWriters.size() > 0)
        {
            resp.setContentType("text/plain");
            final ServletOutputStream out = resp.getOutputStream();

            selectedWriters.forEach(writerName -> {
                List<OutputLogEvent> combinedOutputLogEvents = AwsCloudWatchLogsJsonWriter.getCombinedOutputLogEvents(
                        writerName,
                        req
                );

                try
                {
                    out.println(String.format("%s (%d log events found)", writerName, combinedOutputLogEvents != null ? combinedOutputLogEvents.size() : 0));
                    if (combinedOutputLogEvents != null && combinedOutputLogEvents.size() > 0)
                    {
                        combinedOutputLogEvents.forEach(
                                e -> {
                                    try
                                    {
                                        out.print(Util.getFormattedTimestamp(e.timestamp()));
                                        out.print(" ");
                                        out.println(e.message());
                                    }
                                    catch (IOException ex)
                                    {
                                        ex.printStackTrace();
                                    }
                                }
                        );
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            });
        }
        else
        {
            req.setAttribute("selectedWriters", selectedWriters);

            dispatcher.forward(req, resp);
        }
    }

    private List<String> getSelectedWriters(Map<String, String[]> parameterMap)
    {
        return parameterMap.entrySet()
                .stream()
                .filter(p -> (p.getKey().startsWith(AwsCloudWatchLogsJsonWriter.WRITER_PREFIX) && p.getValue() != null && p.getValue().length > 0 && Boolean.parseBoolean(p.getValue()[0])))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
