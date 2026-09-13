package com.plantstorage.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.eclipse.jetty.ee10.servlet.*;
import org.eclipse.jetty.http.HttpCookie;
import java.io.IOException;
import java.util.*;

public final class SecuritySetup {
    private SecuritySetup() {}
    public static void install(ServletContextHandler context, AuthSettings settings) {
        context.getSessionHandler().setMaxInactiveInterval(settings.idleSeconds());
        SessionCookieConfig cookies = context.getSessionHandler().getSessionCookieConfig();
        cookies.setName("PLANTSESSION"); cookies.setPath("/"); cookies.setHttpOnly(true);
        cookies.setSecure(settings.secureCookies());
        context.getSessionHandler().setSameSite(HttpCookie.SameSite.STRICT);
        context.getSessionHandler().setSessionTrackingModes(Set.of(SessionTrackingMode.COOKIE));
        context.addFilter(new FilterHolder(new AuthFilter(settings)), "/*", EnumSet.allOf(DispatcherType.class));
        context.addServlet(new ServletHolder(new AuthServlet(settings)), "/auth/*");
        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setContentType("text/plain"); resp.getWriter().write("ok");
            }
        }), "/healthz");
    }
}
