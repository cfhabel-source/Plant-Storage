package com.plantstorage.security;

import com.plantstorage.controllers.IdentificationServlet;
import jakarta.servlet.http.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.ee10.servlet.*;
import org.eclipse.jetty.util.resource.ResourceFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only server: production authentication around disposable data, never Google Drive. */
public final class SecurityFixture implements AutoCloseable {
    public final Server server;
    public final AtomicInteger mutations = new AtomicInteger();
    public final String origin;
    public SecurityFixture(int port, String cookieOrigin, String hash) throws Exception {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1"); connector.setPort(port); server.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.setBaseResource(ResourceFactory.of(context).newResource(Path.of("src/main/webapp").toAbsolutePath()));
        context.setWelcomeFiles(new String[]{"index.html"});
        // Tests use a fixed port so the configured browser origin can be exact.
        origin = "http://localhost:" + port;
        SecuritySetup.install(context, new AuthSettings("test-client", hash, cookieOrigin == null ? origin : cookieOrigin, 60));
        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                if (req.getParameter("expire") != null) req.getSession().setAttribute(AuthFilter.LOGIN_TIME, 1L);
                resp.setContentType("application/json");
                resp.getWriter().write("[{\"id\":1,\"name\":\"<img src=x onerror=alert(1)>\",\"species\":\"Test plant\",\"photos\":[]}]");
            }
            @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                mutations.incrementAndGet(); resp.setContentType("application/json"); resp.getWriter().write("{\"status\":\"Plant added\"}");
            }
            @Override protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException { doPost(req, resp); }
            @Override protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException { doPost(req, resp); }
        }), "/plants/*");
        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setHeader("Cache-Control", "private, max-age=300");
                resp.setContentType("text/plain"); resp.getWriter().write("private photo fixture");
            }
        }), "/photos/*");
        context.addServlet(new ServletHolder(new IdentificationServlet()), "/identify/*");
        ServletHolder files = new ServletHolder(DefaultServlet.class);
        files.setInitParameter("dirAllowed", "false"); context.addServlet(files, "/");
        server.setHandler(context); server.start();
    }
    @Override public void close() throws Exception { server.stop(); }
    public static void main(String[] args) throws Exception {
        SecurityFixture fixture = new SecurityFixture(8090, null, PasswordHash.create("temporary-fixture-password".toCharArray()));
        System.out.println("Disposable browser fixture ready on localhost:8090");
        fixture.server.join();
    }
}
