package com.artish.proxy;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForwardProxyServer {

    private final static Logger logger = LoggerFactory.getLogger(ForwardProxyServer.class);

    private static final int DEFAULT_PARENT_PROXY_PORT = 33128;

    private Server server;
    private ExecutorService executor;
    private volatile boolean isStarted = false;

    public ForwardProxyServer(int localProxyPort, String parentProxyHost) {
        server = new Server(localProxyPort);

        // Setup proxy handler to handle CONNECT methods
        ChainedProxyConnectHandler proxy = new ChainedProxyConnectHandler(parentProxyHost, DEFAULT_PARENT_PROXY_PORT);
        server.setHandler(proxy);

        // Setup proxy servlet
        ServletContextHandler context = new ServletContextHandler(proxy, "/", ServletContextHandler.SESSIONS);
        ServletHolder proxyServlet = new ServletHolder(ProxyServlet.Transparent.class);
        proxyServlet.setInitParameter("ProxyTo", "http://" + parentProxyHost + ":" + DEFAULT_PARENT_PROXY_PORT + "/");
        proxyServlet.setInitParameter("Prefix", "/");
        context.addServlet(proxyServlet, "/*");
    }

    public void startProxy() {
        if (isStarted) {
            logger.debug("ForwardProxyServer is already started");
            return;
        }
        logger.debug("ForwardProxyServer: starting");
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                isStarted = true;
                server.start();
            } catch (Exception e) {
                isStarted = false;
                logger.error(e.getMessage());
            }
        });
        logger.debug("ForwardProxyServer started");
    }

    public void stopProxy() {
        if (server != null && isStarted) {
            try {
                server.stop();
                executor.shutdown();
            } catch (Exception e) {
                logger.error(e.getMessage());
            } finally {
                isStarted = false;
                logger.debug("ForwardProxyServer stopped");
            }
        }
    }

}