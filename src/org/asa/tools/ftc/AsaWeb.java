package org.asa.tools.ftc;

import org.asa.tools.core.Live;
import org.asa.tools.core.Recorder;
import org.asa.tools.core.WpiLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Tiny web UI on the Control Hub: http://192.168.43.1:8099/ (from any laptop/phone on the robot Wi-Fi).
 * One background thread, read-only, serves only files inside the ASA log folder.
 * Stays up across OpModes once started; if the port is busy the robot simply runs without it.
 */
public final class AsaWeb {

    public static final int DEFAULT_PORT = 8099;

    private static AsaWeb instance;
    private static volatile Live live;
    private static volatile String status = "";

    private final File dir;
    private volatile String error = "";
    private ServerSocket socket;
    private String url = "";

    private AsaWeb(File dir) { this.dir = dir; }

    /** Start once (later calls return the running server). */
    public static synchronized AsaWeb start() { return start(Recorder.defaultDir(), DEFAULT_PORT); }

    public static synchronized AsaWeb start(File dir, int port) {
        if (instance != null) return instance;
        final AsaWeb w = new AsaWeb(dir);
        ServerSocket bound;
        try {
            bound = bind(port);
        } catch (Exception busy) {
            // OnBot Java reloads code without restarting the app: an older copy of this class may still own the port
            askOldServerToQuit(port);
            try { bound = bind(port); } catch (Exception e) {
                w.error = "web UI off: port " + port + " busy (" + e.getMessage() + ")";
                return w;   // not stored: a later start() may succeed
            }
        }
        final ServerSocket server = bound;
        w.socket = server;
        Thread t = new Thread(new Runnable() { public void run() { w.serve(server); } }, "asa-web");
        t.setDaemon(true);
        t.start();
        w.url = "http://" + ip() + ":" + port + "/";
        instance = w;
        return w;
    }

    /** Show this on the Live tab, e.g. a BlackBox (null to clear). */
    public static void publish(Live source) { live = source; }

    /** Free text shown on the Live tab (e.g. SysId progress). */
    public static void status(String text) { status = text == null ? "" : text; }

    /** "http://<ip>:<port>/" when running, else "". */
    public static String url() {
        AsaWeb w = instance;
        return w == null ? "" : w.url;
    }

    public String error() { return error; }

    // ---------------------------------------------------------------- HTTP

    private static ServerSocket bind(int port) throws Exception {
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(port));
        return s;
    }

    private static void askOldServerToQuit(int port) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", port), 500);
            s.setSoTimeout(1000);
            s.getOutputStream().write("GET /api/quit HTTP/1.0\r\n\r\n".getBytes("UTF-8"));
            s.getInputStream().read();
            s.close();
            Thread.sleep(200);
        } catch (Exception ignored) {}
    }

    private void serve(ServerSocket server) {
        while (!server.isClosed()) {
            Socket s = null;
            try {
                s = server.accept();
                s.setSoTimeout(30000);   // a long session's WPILOG download takes a while
                handle(s);
            } catch (Throwable ignored) {
                // one bad request must not stop the server
            } finally {
                if (s != null) try { s.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void handle(Socket s) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        String line = in.readLine();
        if (line == null) return;
        while (true) { String h = in.readLine(); if (h == null || h.isEmpty()) break; }
        String[] parts = line.split(" ");
        OutputStream out = s.getOutputStream();
        if (parts.length < 2 || !parts[0].equals("GET")) { send(out, 405, "text/plain", bytes("GET only")); return; }
        String path = parts[1];
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        path = URLDecoder.decode(path, "UTF-8");

        if (path.equals("/api/quit")) {
            if (!s.getInetAddress().isLoopbackAddress()) { send(out, 400, "text/plain", bytes("local only")); return; }
            send(out, 200, "text/plain", bytes("bye"));
            socket.close();
            return;
        }
        if (path.equals("/")) send(out, 200, "text/html; charset=utf-8", bytes(AsaWebPage.html()));
        else if (path.equals("/api/live")) send(out, 200, "application/json", bytes(liveJson()));
        else if (path.equals("/api/files")) send(out, 200, "application/json", bytes(filesJson()));
        else if (path.startsWith("/file/")) sendFile(out, path.substring(6));
        else if (path.startsWith("/wpilog/")) sendWpiLog(out, path.substring(8));
        else send(out, 404, "text/plain", bytes("not found"));
    }

    /** Only "name" or "sysid/name" with safe characters: no "..", no absolute paths. */
    private void sendFile(OutputStream out, String rel) throws Exception {
        if (!rel.matches("(sysid/)?[A-Za-z0-9_.-]+") || rel.contains("..")) { send(out, 400, "text/plain", bytes("bad name")); return; }
        File f = new File(dir, rel);
        if (!f.isFile()) { send(out, 404, "text/plain", bytes("not found")); return; }
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + f.length()
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        } finally { in.close(); }
        out.flush();
    }

    /** A Black Box session converted to WPILOG on the fly for AdvantageScope (streamed, never fully in memory). */
    private void sendWpiLog(OutputStream out, String rel) throws Exception {
        if (!rel.matches("[A-Za-z0-9_.-]+\\.csv") || rel.contains("..")) { send(out, 400, "text/plain", bytes("bad name")); return; }
        File f = new File(dir, rel);
        if (!f.isFile()) { send(out, 404, "text/plain", bytes("not found")); return; }
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=\""
                + rel.replace(".csv", ".wpilog") + "\"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        OutputStream buffered = new java.io.BufferedOutputStream(out, 65536);
        WpiLog.convert(f, buffered);
        buffered.flush();
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws Exception {
        String reason = code == 200 ? "OK" : code == 404 ? "Not Found" : code == 405 ? "Method Not Allowed" : "Bad Request";
        out.write(("HTTP/1.1 " + code + " " + reason + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }

    private static byte[] bytes(String s) throws Exception { return s.getBytes("UTF-8"); }

    // ---------------------------------------------------------------- JSON

    private String liveJson() {
        StringBuilder j = new StringBuilder("{\"status\":").append(str(status)).append(",\"names\":[");
        Live src = live;
        List<String> names = src == null ? new ArrayList<String>() : src.names();
        double[] v = src == null ? new double[0] : src.values();
        int n = Math.min(names.size(), v.length);
        for (int i = 0; i < n; i++) j.append(i > 0 ? "," : "").append(str(names.get(i)));
        j.append("],\"values\":[");
        for (int i = 0; i < n; i++) j.append(i > 0 ? "," : "").append(Double.isNaN(v[i]) || Double.isInfinite(v[i]) ? "null" : Double.toString(v[i]));
        j.append("],\"warnings\":[");
        List<String> w = src == null ? new ArrayList<String>() : src.warnings();
        for (int i = 0; i < w.size(); i++) j.append(i > 0 ? "," : "").append(str(w.get(i)));
        return j.append("]}").toString();
    }

    private String filesJson() {
        List<String> all = new ArrayList<String>();
        String[] top = dir.list();
        if (top != null) for (String f : top) if (new File(dir, f).isFile()) all.add(f);
        String[] sys = new File(dir, "sysid").list();
        if (sys != null) for (String f : sys) all.add("sysid/" + f);
        Collections.sort(all, Collections.reverseOrder());   // names start with yyyyMMdd_HHmmss: newest first
        StringBuilder j = new StringBuilder("[");
        for (int i = 0; i < all.size(); i++) j.append(i > 0 ? "," : "").append(str(all.get(i)));
        return j.append(']').toString();
    }

    private static String str(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    /** Control Hub Wi-Fi is 192.168.43.1; prefer that subnet over USB/Ethernet interfaces. */
    private static String ip() {
        String first = null;
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); )
                for (Enumeration<InetAddress> a = e.nextElement().getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress x = a.nextElement();
                    if (x.isLoopbackAddress() || x.getAddress().length != 4) continue;
                    if (x.getHostAddress().startsWith("192.168.43.")) return x.getHostAddress();
                    if (first == null) first = x.getHostAddress();
                }
        } catch (Exception ignored) {}
        return first != null ? first : "192.168.43.1";
    }
}
