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
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        w.url = "http://" + ip() + ":" + port + "/";
        instance = w;
        return w;
    }

    /** Stops the page server (Remove ASA). */
    public static synchronized void stop() {
        AsaWeb w = instance;
        instance = null;
        live = null;
        if (w != null && w.socket != null) try { w.socket.close(); } catch (Exception ignored) {}
    }

    /** Show this on the Live tab, e.g. a BlackBox (null to clear). */
    public static void publish(Live source) { live = source; }

    /** Clears the Live tab only if it still shows this source (a team's own Black Box may have replaced it). */
    public static synchronized void unpublish(Live source) { if (live == source) live = null; }

    /** Set by the automatic recorder's hook: an OpMode is INITed or running, whether or not anything records it. */
    private static volatile boolean opModeActive;
    static void opModeActive(boolean on) { opModeActive = on; }

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

    /**
     * At most this many requests at once. This runs on the robot during matches: anyone on the robot Wi-Fi opening
     * many slow connections must get "busy", never fill the Control Hub's memory with threads.
     */
    static final int MAX_REQUESTS = 8;
    private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(MAX_REQUESTS);

    /** The request line and all headers together: anything longer is not a browser asking for a page. */
    static final int MAX_REQUEST_BYTES = 8192;
    /** Whole-connection deadlines, enforced by the reaper (a read timeout alone lets a byte every 3 s keep a slot). */
    static final long REQUEST_MS = 5000, RESPONSE_MS = 120000;

    /** Open connections and when each must be finished; the reaper closes late ones, even a thread stuck writing. */
    private final java.util.Map<Socket, Long> deadlines = new java.util.concurrent.ConcurrentHashMap<Socket, Long>();

    private void serve(final ServerSocket server) {
        Thread reaper = new Thread(new Runnable() { public void run() {
            while (!server.isClosed()) {
                long now = System.nanoTime();
                for (java.util.Map.Entry<Socket, Long> e : deadlines.entrySet())
                    if (now - e.getValue() > 0) { try { e.getKey().close(); } catch (Exception ignored) {} deadlines.remove(e.getKey()); }
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            }
        }}, "asa-web-reaper");
        reaper.setDaemon(true);
        reaper.setPriority(Thread.MIN_PRIORITY);
        reaper.start();
        while (!server.isClosed()) {
            try {
                final Socket s = server.accept();
                s.setSoTimeout((int) REQUEST_MS);
                if (!slots.tryAcquire()) {
                    try { send(s.getOutputStream(), 503, "text/plain", bytes("busy")); } catch (Throwable ignored) {}
                    try { s.close(); } catch (Exception ignored) {}
                    continue;
                }
                deadlines.put(s, System.nanoTime() + REQUEST_MS * 1000000L);
                // one thread per request (bounded): a phone downloading a long WPILOG must not freeze the Live tab for the others
                Thread t = new Thread(new Runnable() { public void run() {
                    try { handle(s); } catch (Throwable ignored) {}   // one bad request must not stop the server
                    finally { deadlines.remove(s); try { s.close(); } catch (Exception ignored) {} slots.release(); }
                }}, "asa-web-req");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);   // a download during a match must not slow the OpMode
                try { t.start(); } catch (Throwable e) { deadlines.remove(s); slots.release(); try { s.close(); } catch (Exception ignored) {} }
            } catch (Throwable e) {
                if (!server.isClosed()) try { Thread.sleep(100); } catch (InterruptedException ie) { return; }   // e.g. no file descriptors: do not spin
            }
        }
    }

    /** The request line, after reading at most MAX_REQUEST_BYTES up to the blank line; null when too long or cut off. */
    private static String readRequestLine(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int lineStart = 0;
        for (int b; (b = in.read()) != -1; ) {
            if (buf.size() >= MAX_REQUEST_BYTES) return null;
            buf.write(b);
            if (b == '\n') {
                byte[] all = buf.toByteArray();
                int len = all.length - lineStart;                       // this line, with its \n (and \r)
                if (len <= 2 && lineStart > 0) {                       // blank line: end of the headers
                    String req = new String(all, 0, all.length, "UTF-8");
                    int end = req.indexOf('\n');
                    return req.substring(0, end).trim();
                }
                lineStart = all.length;
            }
        }
        return null;
    }

    private void handle(Socket s) throws Exception {
        String line = readRequestLine(s.getInputStream());
        if (line == null) return;
        deadlines.put(s, System.nanoTime() + RESPONSE_MS * 1000000L);   // sending: a long session's WPILOG takes a while
        s.setSoTimeout((int) RESPONSE_MS);
        String[] parts = line.split(" ");
        OutputStream out = s.getOutputStream();
        if (parts.length < 2 || !parts[0].equals("GET")) { send(out, 405, "text/plain", bytes("GET only")); return; }
        String path = parts[1];
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        try { path = URLDecoder.decode(path, "UTF-8"); } catch (IllegalArgumentException bad) { send(out, 400, "text/plain", bytes("bad request")); return; }

        if (path.equals("/api/quit")) {
            if (!s.getInetAddress().isLoopbackAddress()) { send(out, 400, "text/plain", bytes("local only")); return; }
            send(out, 200, "text/plain", bytes("bye"));
            synchronized (AsaWeb.class) { if (instance == this) instance = null; }   // a later start() binds a fresh server
            socket.close();
            return;
        }
        if (path.equals("/")) send(out, 200, "text/html; charset=utf-8", bytes(AsaWebPage.html()));
        else if (path.equals("/api/live")) send(out, 200, "application/json", bytes(liveJson()));
        else if (path.equals("/api/files")) send(out, 200, "application/json", bytes(filesJson()));
        else if (path.equals("/api/tools")) send(out, 200, "application/json",
                bytes("{\"blackbox\":" + installed("BlackBox", "blackbox") + ",\"sysid\":" + installed("SysIdAnalyzer", "sysid") + "}"));
        else if (path.startsWith("/file/")) sendFile(out, path.substring(6));
        else if (path.startsWith("/wpilog/")) sendWpiLog(out, path.substring(8));
        else send(out, 404, "text/plain", bytes("not found"));
    }

    /**
     * The Driver Station controls the robot over the same Wi-Fi: while a program drives, the page gets only small
     * answers (live values, the page, the file list) and no recording over this size, nor any WPILOG.
     */
    static final long MAX_BYTES_WHILE_DRIVING = 256 * 1024;

    private static boolean driving() {
        if (opModeActive) return true;
        Live src = live;
        try { return src != null && !src.names().isEmpty(); } catch (Throwable e) { return false; }
    }

    /** Only "name" or "sysid/name" with safe characters: no "..", no absolute paths. */
    private void sendFile(OutputStream out, String rel) throws Exception {
        if (!rel.matches("(sysid/)?[\\p{L}\\p{N}_.-]+") || rel.contains("..")) { send(out, 400, "text/plain", bytes("bad name")); return; }
        File f = new File(dir, rel);
        if (!f.isFile()) { send(out, 404, "text/plain", bytes("not found")); return; }
        if (driving() && f.length() > MAX_BYTES_WHILE_DRIVING) { send(out, 503, "text/plain", bytes("driving")); return; }
        boolean big = f.length() > MAX_BYTES_WHILE_DRIVING;
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + f.length()
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        OutputStream o = big ? stopWhenDriving(out) : out;   // a download started before INIT stops when the robot drives
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
        } finally { in.close(); }
        o.flush();
    }

    /** A Black Box session converted to WPILOG on the fly for AdvantageScope (streamed, never fully in memory). */
    private void sendWpiLog(OutputStream out, String rel) throws Exception {
        if (!rel.matches("[\\p{L}\\p{N}_.-]+\\.csv") || rel.contains("..") || rel.endsWith(".events.csv") || rel.endsWith(".summary.csv")) {
            send(out, 400, "text/plain", bytes("bad name")); return;
        }
        if (driving()) { send(out, 503, "text/plain", bytes("driving")); return; }
        File f = new File(dir, rel);
        if (!f.isFile()) { send(out, 404, "text/plain", bytes("not found")); return; }
        String name = rel.substring(0, rel.length() - 4) + ".wpilog";
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=\""
                + name.replaceAll("[^\\x20-\\x7E]", "_") + "\"; filename*=UTF-8''"   // real name ("Маршрут") for browsers that read it
                + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20") + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        OutputStream buffered = new java.io.BufferedOutputStream(stopWhenDriving(out), 65536);
        WpiLog.convert(f, buffered);
        buffered.flush();
    }

    /** Passes bytes on until a program starts driving, then fails the write: the Wi-Fi is the Driver Station's. */
    private static OutputStream stopWhenDriving(final OutputStream out) {
        return new java.io.FilterOutputStream(out) {
            @Override public void write(byte[] b, int off, int len) throws java.io.IOException {
                if (driving()) throw new java.io.IOException("robot driving");
                out.write(b, off, len);
            }
            @Override public void write(int b) throws java.io.IOException {
                if (driving()) throw new java.io.IOException("robot driving");
                out.write(b);
            }
        };
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws Exception {
        String reason = code == 200 ? "OK" : code == 404 ? "Not Found" : code == 405 ? "Method Not Allowed" : code == 503 ? "Service Unavailable" : "Bad Request";
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

    /** Which ASA library is on the robot: the page then shows only that one ("ASA Black Box" or "ASA SysId"). */
    private static boolean installed(String cls, String pkg) {
        for (String n : new String[]{"org.asa.tools." + pkg + "." + cls, "org.firstinspires.ftc.teamcode." + cls}) {
            try { Class.forName(n); return true; } catch (Throwable ignored) {}
        }
        return false;
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
