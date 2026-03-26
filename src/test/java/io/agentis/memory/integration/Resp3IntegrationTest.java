package io.agentis.memory.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Tag("docker")
class Resp3IntegrationTest extends AbstractIntegrationTest {

    @Test
    void testHello3Raw() throws Exception {
        try (Socket socket = new Socket(container.getHost(), container.getMappedPort(CONTAINER_PORT))) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write("*2\r\n$5\r\nHELLO\r\n$1\r\n3\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            byte[] buffer = new byte[1024];
            int read = in.read(buffer);
            String response = new String(buffer, 0, read, StandardCharsets.UTF_8);
            
            assertTrue(response.startsWith("%"), "Expected RESP3 Map response (%), got: " + response);
            assertTrue(response.contains("server"));
            assertTrue(response.contains("agentis-memory"));
            assertTrue(response.contains("proto"));
            assertTrue(response.contains(":3"));
        }
    }

    @Test
    void testHgetallResp3Raw() throws Exception {
        try (Socket socket = new Socket(container.getHost(), container.getMappedPort(CONTAINER_PORT))) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // HELLO 3
            out.write("*2\r\n$5\r\nHELLO\r\n$1\r\n3\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            readResponse(in); // Skip HELLO response

            // HSET h3 f1 v1
            out.write("*4\r\n$4\r\nHSET\r\n$2\r\nh3\r\n$2\r\nf1\r\n$2\r\nv1\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            readResponse(in); // Skip HSET response

            // HGETALL h3
            out.write("*2\r\n$7\r\nHGETALL\r\n$2\r\nh3\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = readResponse(in);
            assertTrue(response.startsWith("%1\r\n"), "HGETALL should return a Map in RESP3, got: " + response);
            assertTrue(response.contains("$2\r\nf1\r\n$2\r\nv1\r\n"));
        }
    }

    @Test
    void testSmembersResp3Raw() throws Exception {
        try (Socket socket = new Socket(container.getHost(), container.getMappedPort(CONTAINER_PORT))) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write("*2\r\n$5\r\nHELLO\r\n$1\r\n3\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            readResponse(in);

            out.write("*3\r\n$4\r\nSADD\r\n$2\r\ns3\r\n$2\r\nm1\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            readResponse(in);

            out.write("*2\r\n$8\r\nSMEMBERS\r\n$2\r\ns3\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = readResponse(in);
            assertTrue(response.startsWith("~1\r\n"), "SMEMBERS should return a Set in RESP3, got: " + response);
            assertTrue(response.contains("$2\r\nm1\r\n"));
        }
    }

    @Test
    void testSismemberResp3Raw() throws Exception {
        try (Socket socket = new Socket(container.getHost(), container.getMappedPort(CONTAINER_PORT))) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write("*2\r\n$5\r\nHELLO\r\n$1\r\n3\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            readResponse(in);

            out.write("*3\r\n$4\r\nSADD\r\n$2\r\ns3\r\n$2\r\nm1\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            readResponse(in);

            out.write("*3\r\n$9\r\nSISMEMBER\r\n$2\r\ns3\r\n$2\r\nm1\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = readResponse(in);
            assertEquals("#t\r\n", response, "SISMEMBER should return Boolean in RESP3");
        }
    }

    @Test
    void testZscoreResp3Raw() throws Exception {
        try (Socket socket = new Socket(container.getHost(), container.getMappedPort(CONTAINER_PORT))) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write("*2\r\n$5\r\nHELLO\r\n$1\r\n3\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            readResponse(in);

            out.write("*4\r\n$4\r\nZADD\r\n$2\r\nz3\r\n$3\r\n1.5\r\n$2\r\nm1\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            readResponse(in);

            out.write("*3\r\n$6\r\nZSCORE\r\n$2\r\nz3\r\n$2\r\nm1\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = readResponse(in);
            assertEquals(",1.5\r\n", response, "ZSCORE should return Double in RESP3");
        }
    }

    @Test
    void testGetNullResp3Raw() throws Exception {
        try (Socket socket = new Socket(container.getHost(), container.getMappedPort(CONTAINER_PORT))) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write("*2\r\n$5\r\nHELLO\r\n$1\r\n3\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            readResponse(in);

            out.write("*2\r\n$3\r\nGET\r\n$4\r\nnone\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = readResponse(in);
            assertEquals("_\r\n", response, "GET missing should return Null in RESP3");
        }
    }

    private String readResponse(InputStream in) throws Exception {
        int b = in.read();
        if (b == -1) return null;
        char prefix = (char) b;
        StringBuilder lineSb = new StringBuilder();
        lineSb.append(prefix);
        readLine(in, lineSb);
        String line = lineSb.toString();

        if (prefix == '+' || prefix == '-' || prefix == ':' || prefix == ',' || prefix == '#' || prefix == '(' || prefix == '_') {
            return line;
        } else if (prefix == '$' || prefix == '*' || prefix == '%' || prefix == '~' || prefix == '=') {
            int len = Integer.parseInt(line.substring(1, line.length() - 2));
            if (len == -1) return line;
            
            StringBuilder sb = new StringBuilder(line);
            if (prefix == '$') {
                byte[] data = in.readNBytes(len);
                sb.append(new String(data, StandardCharsets.UTF_8));
                readLine(in, sb); // \r\n
            } else if (prefix == '*') {
                for (int i = 0; i < len; i++) {
                    sb.append(readResponse(in));
                }
            } else if (prefix == '%') {
                for (int i = 0; i < len * 2; i++) {
                    sb.append(readResponse(in));
                }
            } else if (prefix == '~') {
                for (int i = 0; i < len; i++) {
                    sb.append(readResponse(in));
                }
            } else if (prefix == '=') {
                byte[] data = in.readNBytes(len);
                sb.append(new String(data, StandardCharsets.UTF_8));
                readLine(in, sb); // \r\n
            }
            return sb.toString();
        }
        return line;
    }

    private String readLine(InputStream in, StringBuilder sb) throws Exception {
        int b;
        while ((b = in.read()) != -1) {
            sb.append((char) b);
            if (b == '\n' && sb.length() >= 2 && sb.charAt(sb.length() - 2) == '\r') {
                break;
            }
        }
        return sb.toString();
    }
}
