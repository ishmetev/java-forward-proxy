package com.artish.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.channels.SocketChannel;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.handler.ConnectHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChainedProxyConnectHandler extends ConnectHandler {

    private final static Logger logger = LoggerFactory.getLogger(ChainedProxyConnectHandler.class);

    private String parentProxyHost;
    private int parentProxyPort;

    ChainedProxyConnectHandler(String parentProxyHost, int parentProxyPort) {
        super();
        this.parentProxyHost = parentProxyHost;
        this.parentProxyPort = parentProxyPort;
    }

    protected SocketChannel connect(HttpServletRequest request, String host, int port) throws IOException {
        SocketChannel channel = super.connect(request, parentProxyHost, parentProxyPort);
        Socket socket = channel.socket();
        establishConnectHandshake(host, port, socket.getOutputStream(), socket.getInputStream());
        return channel;
    }

    private byte[] str2byte(String str, String encoding) {
        if (str == null)
            return null;
        try {
            return str.getBytes(encoding);
        } catch (java.io.UnsupportedEncodingException e) {
            return str.getBytes();
        }
    }

    private byte[] str2byte(String str) {
        return str2byte(str, "UTF-8");
    }

    private void establishConnectHandshake(String host, int port, OutputStream out, InputStream in) throws IOException {
        String connectMessage = "CONNECT " + host + ":" + port + " HTTP/1.0\r\n" + "Proxy-Connection: Keep-Alive\r\n"
                + "User-Agent: Mozilla/4.0\r\n";

        logger.debug(">>> {}", connectMessage);

        out.write(str2byte(connectMessage));
        out.write(str2byte("\r\n"));
        out.flush();

        int foo = 0;

        StringBuilder sb = new StringBuilder();
        while (foo >= 0) {
            foo = in.read();
            if (foo != 13) {
                sb.append((char) foo);
                continue;
            }
            foo = in.read();
            if (foo != 10) {
                continue;
            }
            break;
        }
        if (foo < 0) {
            throw new IOException();
        }

        String response = sb.toString();
        logger.debug("<<< {}", response);

        String reason = "Unknown reason";
        int code = -1;
        try {
            foo = response.indexOf(' ');
            int bar = response.indexOf(' ', foo + 1);
            code = Integer.parseInt(response.substring(foo + 1, bar));
            reason = response.substring(bar + 1);
        } catch (Exception e) {
        }
        if (code != 200) {
            logger.warn("Unable to handshake with upstream proxy to CONNECT to host=" + host + " port=" + port + " " + "reason:" + reason);
            throw new IOException("proxy error: " + reason);
        }

        int count;
        do {
            count = 0;
            while (foo >= 0) {
                foo = in.read();
                if (foo != 13) {
                    count++;
                    continue;
                }
                foo = in.read();
                if (foo != 10) {
                    continue;
                }
                break;
            }
            if (foo < 0) {
                throw new IOException();
            }
        } while (count != 0);
    }
}