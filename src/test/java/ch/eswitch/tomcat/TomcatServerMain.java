package ch.eswitch.tomcat;

import ch.eswitch.tinylog.writers.AwsCloudWatchLogsViewerServlet;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;

import javax.servlet.annotation.WebServlet;
import java.util.Arrays;
import java.util.Optional;

public class TomcatServerMain
{

    public static final Optional<String> PORT = Optional.ofNullable(System.getenv("PORT"));
    public static final Optional<String> HOSTNAME = Optional.ofNullable(System.getenv("HOSTNAME"));

    public static void main(String[] args) throws LifecycleException
    {
        String contextPath = "/";
        String appBase = ".";
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir("apache-tomcat-9");
        //tomcat.setPort(Integer.valueOf(PORT.orElse("8080")));
        //tomcat.setHostname(HOSTNAME.orElse("localhost"));
        tomcat.getHost().setAppBase(appBase);
        StandardContext ctx = (StandardContext) tomcat.addWebapp(contextPath, appBase);

        // add servlet
        final String servletName = AwsCloudWatchLogsViewerServlet.class.getSimpleName();
        tomcat.addServlet(ctx, servletName, new AwsCloudWatchLogsViewerServlet());
        WebServlet webServletAnnotation = AwsCloudWatchLogsViewerServlet.class.getAnnotation(WebServlet.class);
        Arrays.stream(webServletAnnotation.urlPatterns())
                .forEach(u -> ctx.addServletMappingDecoded(u, servletName)
                );

        System.out.println("start Tomcat");
        tomcat.start();
        System.out.println(String.format("Tomcat started on %s://%s:%d", tomcat.getConnector().getScheme(), tomcat.getServer().getAddress(), tomcat.getConnector().getPort()));
        tomcat.getServer().await();
        System.out.println("Tomcat stopped");
    }
}