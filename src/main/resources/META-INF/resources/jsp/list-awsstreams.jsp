<%@ page import="ch.eswitch.tinylog.writers.AwsCloudWatchLogsJsonWriter" %>
<%@ page import="software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.time.Instant" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%!
    public static final String CSS_STYLE_WHITE_SPACE_NOWRAP = "white-space: nowrap;";
    private int collapseCell = 0;
    private static final int MAX_TEXT_LENGTH = 200;

    private void printColumn(JspWriter out, Long millis)
    {
        if (millis != null && millis > 0)
        {
            printColumn(out, DateTimeFormatter.ISO_DATE_TIME.format(Instant.ofEpochMilli(millis)
                                                                           .atZone(ZoneId.systemDefault())
                                                                           .toLocalDateTime()),
                        CSS_STYLE_WHITE_SPACE_NOWRAP, null);
        }
    }

    private void printColumn(JspWriter out, String text)
    {
        printColumn(out, text, null, null);
    }

    private void printColumn(JspWriter out, String text, String style, String ref)
    {
        try
        {
            out.print("<td");
            if (style != null)
            {
                out.print(" style=\"");
                out.print(style);
                out.print("\"");
            }
            out.println(">");

            if (text.length() < MAX_TEXT_LENGTH)
            {
                printText(out, text, ref);
            }
            else
            {
                out.print("<a data-bs-toggle=\"collapse\" href=\"#collapseCell");
                out.print(collapseCell);
                out.print("\">");
                out.print(text.substring(0, MAX_TEXT_LENGTH));
                out.println("...</a>");

                out.print("<div class=\"collapse multi-collapse\" id=\"collapseCell");
                out.print(collapseCell);
                out.println("\">");
                out.println("<div class=\"card card-body\">");
                printText(out, text, ref);
                out.println("</div>");
                out.println("</div>");

                collapseCell++;
            }
            out.println("</td>");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    private void printText(JspWriter out, String text, String ref) throws IOException
    {
        if (ref != null)
        {
            out.print("<div id=\"");
            out.print(ref);
            out.println("\">");
        }
        out.println(text);
        if (ref != null)
        {
            out.println("</div>");
        }
    }
%>

<html>
<head>
    <title>AWS CloudWatch tinylog Viewer</title>
    <link rel="stylesheet" href="bootstrap/css/bootstrap.min.css">
    <script src="bootstrap/js/bootstrap.bundle.min.js"></script>
    <link rel="stylesheet" href="bootstrap/font/bootstrap-icons.css">

    <script type="text/javascript">
        function downloadData(ref) {
            var content = document.getElementById(ref).innerText;

            var contentType = 'text/plain';
            var fileExtension = 'txt';
            try {
                JSON.parse(content);
                contentType = 'application/json'
                fileExtension = 'json';
            } catch (e) {
            }

            var a = document.createElement('a');
            var blob = new Blob([content], {'type': contentType});
            a.href = window.URL.createObjectURL(blob);
            a.download = 'logEntry.' + fileExtension;
            a.click();
        }
    </script>
</head>


<body>
<div class="container">
    <h2>AWS CloudWatch tinylog Viewer</h2>

    <c:if test="${not empty message}">
        <div class="alert alert-success">
                ${message}
        </div>
    </c:if>

    <form action="/awslogviewer" method="post" id="writerForm" role="form">
        <input type="hidden" id="idWriter" name="idWriter">
        <input type="hidden" id="action" name="action">

        <p class="d-inline-flex gap-1">
            <!--
            <button type="button" class="btn-close" data-bs-toggle="collapse" aria-expanded="true"
                    aria-controls="collapseSearch" aria-label="Close" role="button"/>
                    -->
            <a class="btn btn-primary" data-bs-toggle="collapse" href="#collapseSearch" role="button"
               aria-controls="collapseSearch">
                Search Parameter
            </a>

        </p>
        <div class="collapse <c:if test="${!param.submitButton.equals('true')}">show</c:if>" id="collapseSearch">
            <div class="card card-body">

                <h3 class="mt-3">Search Criteria</h3>
                <div class="row">
                    <div class="col">
                        <label for="startDateTime" class="form-label">Start</label>
                        <input type="datetime-local" width="auto" class="form-control" id="startDateTime"
                               name="startDateTime"
                               placeholder="Start" value="${param.startDateTime}" required/>
                    </div>
                    <div class="col">
                        <label for="endDateTime" class="form-label">End</label>
                        <input type="datetime-local" width="auto" class="form-control" id="endDateTime"
                               name="endDateTime"
                               placeholder="End"
                               value="${param.endDateTime}"/>
                    </div>
                </div>
                <div class="mt-3">
                    <label for="searchTerm" class="form-label">Search Term</label>
                    <input type="text" class="form-control" id="searchTerm" name="searchTerm"
                           aria-describedby="searchHelp"
                           placeholder="search term" value="${param.searchTerm}"/>
                    <small id="searchHelp" class="form-text text-muted">text or <a
                            href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html"
                            target="_blank">Java RegEx Pattern</a></small>
                </div>
                <div class="form-check">
                    <input type="checkbox" class="form-check-input" id="useRegExp" name="useRegExp"
                           value="true" <c:if test="${param.useRegExp.equals('true')}">checked="true"</c:if>/>
                    <label for="useRegExp" class="form-check-label">use RegExp</label>
                </div>
                <div class="mt-3">
                    <label for="outputType" class="form-label">Output Type</label>
                    <select class="form-select" id="outputType" name="outputType">
                        <option value="html" <c:if test="${param.outputType.equals('html')}">selected="true"</c:if>>
                            HTML
                        </option>
                        <option value="file" <c:if test="${param.outputType.equals('file')}">selected="true"</c:if>>
                            Text file (raw)
                        </option>
                    </select>
                </div>

                <h3 class="mt-3">tinylog Writers</h3>
                <!--Writer List-->
                <c:choose>
                    <c:when test="${not empty requestScope.writerList}">
                        <table class="table table-striped">
                            <thead>
                            <tr>
                                <th>Select</th>
                                <th>Writer Name</th>
                            </tr>
                            </thead>
                            <c:forEach var="writerName" items="${writerList}">
                                <tr class="${classSucess}">
                                    <td><input type="checkbox" id="${writerName}" name="${writerName}" value="true"
                                               <c:if test="${selectedWriters.contains(writerName)}">checked="true"</c:if> >
                                    </td>
                                    <td>${fn:substringAfter(writerName, '_')}</td>
                                </tr>
                            </c:forEach>
                        </table>
                        <button type="submit" class="btn btn-primary" id="submitButton" name="submitButton"
                                value="true">
                            Search Log Events
                        </button>

                        <!-- TODO generate raw log output file -->
                    </c:when>
                    <c:otherwise>
                        <br>
                        <div class="alert alert-warning" role="alert">
                            No configured writers found
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </form>
    <%
        List<String> selectedWriters = (List<String>) request.getAttribute("selectedWriters");
        if (selectedWriters != null && selectedWriters.size() > 0)
        {
            out.println("<h3 class=\"mt-5\">Log Events</h3>");

            final JspWriter outFinal = out;
            selectedWriters.forEach(writerName -> {
                try
                {
                    outFinal.println("<h4>" + writerName.substring(writerName.indexOf('_') + 1) + "</h4>");

                    List<OutputLogEvent> combinedOutputLogEvents = AwsCloudWatchLogsJsonWriter.getCombinedOutputLogEvents(
                            writerName,
                            request.getParameter("startDateTime"),
                            request.getParameter("endDateTime"),
                            request.getParameter("searchTerm"),
                            request.getParameter("useRegExp")
                    );

                    if (combinedOutputLogEvents != null && combinedOutputLogEvents.size() > 0)
                    {
                        outFinal.println(combinedOutputLogEvents.size() + " Log Events found");

                        outFinal.println("<table class=\"table table-striped\">\n"
                                                 + "                    <thead>\n"
                                                 + "                    <tr>\n"
                                                 + "                        <th>Timestamp</th>\n"
                                                 + "                        <th>Message</th>\n"
                                                 + "                        <th></th>\n"
                                                 + "                    </tr>\n"
                                                 + "                    </thead>");

                        Iterator<OutputLogEvent> itEvents = combinedOutputLogEvents.iterator();
                        long refId = 0;
                        while (itEvents.hasNext())
                        {
                            OutputLogEvent e = itEvents.next();

                            outFinal.println("<tr>");

                            printColumn(outFinal, e.timestamp());

                            String ref = writerName + refId++;

                            printColumn(outFinal, e.message(), null, ref);
                            printColumn(outFinal,
                                        "<i class=\"bi bi-download\" onClick=\"downloadData('" + ref + "')\"></i>");

                            outFinal.println("</tr>");
                        }

                        outFinal.println("</table>");

                    }
                    else
                    {
                        outFinal.println("<div class=\"alert alert-warning\" role=\"alert\">"
                                                 + "No Log Events found"
                                                 + "</div>");
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            });
        }
    %>

</div>
</body>
</html>
