<%@ page import="ch.eswitch.tinylog.writers.AwsCloudWatchLogsJsonWriter" %>
<%@ page import="com.google.gson.JsonElement" %>
<%@ page import="com.google.gson.JsonObject" %>
<%@ page import="com.google.gson.JsonParseException" %>
<%@ page import="com.google.gson.JsonParser" %>
<%@ page import="org.apache.commons.lang3.StringEscapeUtils" %>
<%@ page import="software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.time.Instant" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%! public static final String JSON_TAG_MESSAGE = "message";
    public static final String CSS_STYLE_WHITE_SPACE_NOWRAP = "white-space: nowrap;";
    private int collapseCell = 0;
    private static final int MAX_TEXT_LENGTH = 300;

    private void printFunctions(JspWriter out, String ref, boolean rowspan)
    {
        printColumn(out,
                    "<i class=\"bi bi-clipboard\" onClick=\"copyData('" + ref + "')\"></i>", rowspan);
        printColumn(out,
                    "<i class=\"bi bi-download\" onClick=\"downloadData('" + ref + "')\"></i>", rowspan);
    }

    private void printColumn(JspWriter out, Long millis, boolean rowspan)
    {
        if (millis != null && millis > 0)
        {
            printColumn(out, DateTimeFormatter.ISO_DATE_TIME.format(Instant.ofEpochMilli(millis)
                                                                           .atZone(ZoneId.systemDefault())
                                                                           .toLocalDateTime()),
                        CSS_STYLE_WHITE_SPACE_NOWRAP, null, rowspan, -1);
        }
    }

    private void printColumn(JspWriter out, String text, boolean rowspan)
    {
        printColumn(out, text, null, null, rowspan, -1);
    }

    private void printColumn(JspWriter out, String text, String style, String ref, boolean rowspan, int colspan)
    {
        try
        {
            out.print("<td");
            if (rowspan)
            {
                out.print(" rowspan=\"2\"");
            }
            if (colspan > 1)
            {
                out.print(" colspan=\"" + colspan + "\"");
            }
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

        function timeRadiosChanged(element) {
            var disableAbsolute = true;
            var disableRelative = true;
            if (element == null) {
                if (document.getElementById('absoluteRadio').checked)
                    element = document.getElementById('absoluteRadio');
                else if (document.getElementById('relativeRadio').checked)
                    element = document.getElementById('relativeRadio');
            }

            if (element != null) {
                window.console.log(element.id + ": " + element.checked);
                if (element.id == 'absoluteRadio') {
                    disableAbsolute = !element.checked;
                    disableRelative = !disableAbsolute;
                } else if (element.id == 'relativeRadio') {
                    disableRelative = !element.checked;
                    disableAbsolute = !disableRelative;
                }
            }

            document.getElementById('startDateTime').disabled = disableAbsolute;
            document.getElementById('endDateTime').disabled = disableAbsolute;

            document.getElementById('relativeTime').disabled = disableRelative;
            document.getElementById('relativeUnit').disabled = disableRelative;
        }

        document.addEventListener('readystatechange', event => {
            switch (document.readyState) {
                case "loading":
                    break;
                case "interactive":
                    break;
                case "complete":
                    timeRadiosChanged(null);
                    break;
            }
        });


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

        function copyData(ref) {
            var content = document.getElementById(ref).innerText;

            navigator.clipboard.writeText(content);

            var liveToastElement = document.getElementById('liveToast');
            var liveToast = bootstrap.Toast.getOrCreateInstance(liveToastElement);
            liveToast.show();
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

    <form action="awslogviewer" method="post" id="writerForm" role="form">
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

                <h5 class="mt-5">Time Period</h5>

                <div class="form-check">
                    <input class="form-check-input" type="radio" name="timeRadios" id="absoluteRadio"
                           value="absoluteRadio"
                           <c:if test="${param.timeRadios.equals('absoluteRadio')}">checked="true"</c:if> required
                           onchange="timeRadiosChanged(this)">
                    <label class="form-check-label h6" for="absoluteRadio">
                        Absolute Time Period (Start / End)
                    </label>
                </div>

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

                <br>

                <div class="form-check">
                    <input class="form-check-input" type="radio" name="timeRadios" id="relativeRadio"
                           value="relativeRadio"
                           <c:if test="${param.timeRadios.equals('relativeRadio')}">checked="true"</c:if> required
                           onchange="timeRadiosChanged(this)">
                    <label class="form-check-label h6" for="relativeRadio">
                        Relative Time from Now
                    </label>
                </div>

                <div class="row">
                    <div class="col-8">
                        <label class="visually-hidden" for="relativeTime">Time</label>
                        <input type="number" class="form-control" width="auto" id="relativeTime" name="relativeTime"
                               placeholder="Relative Time" min="1" max="60" value="${param.relativeTime}" required/>
                    </div>
                    <div class="col-4">
                        <label class="visually-hidden" for="relativeUnit">Unit</label>
                        <select class="form-select" id="relativeUnit" name="relativeUnit"
                                placeholder="Unit" required>
                            <option value="min" <c:if test="${param.relativeUnit.equals('min')}">selected="true"</c:if>>
                                minutes
                            </option>
                            <option value="h" <c:if test="${param.relativeUnit.equals('h')}">selected="true"</c:if>>
                                hours
                            </option>
                            <option value="d" <c:if test="${param.relativeUnit.equals('d')}">selected="true"</c:if>>
                                days
                            </option>
                        </select>
                    </div>
                </div>

                <div class="mt-3">
                    <label for="searchTerm" class="h5">Search Term</label>
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
                    <label for="outputType" class="h5">Output Type</label>
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
                            request
                    );

                    if (combinedOutputLogEvents != null && combinedOutputLogEvents.size() > 0)
                    {
                        outFinal.println(combinedOutputLogEvents.size() + " Log Events found");

                        Iterator<OutputLogEvent> itEvents = combinedOutputLogEvents.iterator();
                        long refId = 0;
                        boolean isJson = false;
                        boolean printHeader = true;
                        while (itEvents.hasNext())
                        {
                            OutputLogEvent e = itEvents.next();
                            String ref = writerName + refId++;
                            try
                            {
                                JsonElement ele = JsonParser.parseString(e.message());
                                if (ele.isJsonObject())
                                {
                                    JsonObject obj = ele.getAsJsonObject();
                                    Set<Map.Entry<String, JsonElement>> entries = obj.entrySet();
                                    isJson = true;

                                    if (printHeader)
                                    {
                                        printHeader = false;

                                        outFinal.println("<table class=\"table table-striped\">\n"
                                                                 + "                    <thead>\n"
                                                                 + "                    <tr>\n"
                                                                 + "                        <th>Timestamp</th>\n");
                                        for (Map.Entry<String, JsonElement> entry : entries)
                                        {
                                            if (entry.getKey()
                                                     .equalsIgnoreCase(JSON_TAG_MESSAGE))
                                            {
                                                continue;
                                            }

                                            outFinal.println("                        <th>" + entry.getKey() + "</th>");
                                        }
                                        outFinal.println("                        <th></th>\n"
                                                                 + "                    </tr>\n"
                                                                 + "                    </thead>");

                                        outFinal.println("<tr>");
                                    }

                                    printColumn(outFinal, e.timestamp(), true);
                                    for (Map.Entry<String, JsonElement> entry : entries)
                                    {
                                        boolean isMessage = entry.getKey()
                                                                 .equalsIgnoreCase(JSON_TAG_MESSAGE);

                                        if (isMessage)
                                        {
                                            printFunctions(outFinal, ref, true);
                                            outFinal.println("</tr>");
                                            outFinal.println("<tr>");
                                        }

                                        printColumn(outFinal,
                                                    StringEscapeUtils.escapeHtml4(entry.getValue()
                                                                                       .getAsString()),
                                                    null,
                                                    isMessage ? ref : null,
                                                    false,
                                                    isMessage ? entries.size() - 1 : -1);
                                    }

                                }
                            }
                            catch (JsonParseException ex)
                            {
                            }

                            if (!isJson)
                            {
                                if (printHeader)
                                {
                                    printHeader = false;

                                    outFinal.println("<table class=\"table table-striped\">\n"
                                                             + "                    <thead>\n"
                                                             + "                    <tr>\n"
                                                             + "                        <th>Timestamp</th>\n"
                                                             + "                        <th>Message</th>\n"
                                                             + "                        <th></th>\n"
                                                             + "                    </tr>\n"
                                                             + "                    </thead>");

                                    outFinal.println("<tr>");
                                }

                                printColumn(outFinal, e.timestamp(), false);
                                printColumn(outFinal, StringEscapeUtils.escapeHtml4(e.message()), null, ref, false, -1);

                                printFunctions(outFinal, ref, false);
                            }

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

<div class="position-fixed bottom-0 end-0 p-3" style="z-index: 11">
    <div id="liveToast" class="toast hide" role="alert" aria-live="assertive" aria-atomic="true">
        <div class="d-flex">
            <div class="toast-body">
                Message copied to Clipboard
            </div>
            <button type="button" class="btn-close me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
        </div>
    </div>
</div>

</body>
</html>
