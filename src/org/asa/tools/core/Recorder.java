package org.asa.tools.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Shared data core for Black Box and SysId: named signals sampled every update(),
 * timestamped events, CSV files written on a background thread.
 *
 * Never throws into the robot loop: a failing signal reads NaN, a full disk queue drops rows.
 * Plain Java (no FTC imports) so it also runs on a laptop and in tests.
 */
public class Recorder {

    /** A value read every sample. Any exception is caught and recorded as NaN. */
    public interface Source { double get() throws Exception; }

    private final List<String> names = new ArrayList<String>();
    private final List<Source> sources = new ArrayList<Source>();
    private final List<Long> periodNs = new ArrayList<Long>();
    private final Map<String, Integer> index = new HashMap<String, Integer>();

    private double[] values = new double[0];
    private long[] lastReadNs = new long[0];
    private double[] sumAbs, peakAbs;
    private long[] counts, period;
    private boolean[] readNow;
    private long samples;

    private final LinkedList<String> recentEvents = new LinkedList<String>();
    private final BlockingQueue<String[]> queue = new ArrayBlockingQueue<String[]>(8192);
    private static final String[] POISON = new String[0];

    private Thread writer;
    private File dir;
    private String session;
    private long t0Ns;
    private volatile boolean started;
    private volatile long dropped;
    private volatile String writeError;
    private int maxSlowReads = 2;

    // ---------------------------------------------------------------- setup

    /** Register a signal read every update(). */
    public Recorder signal(String name, Source source) { return signal(name, source, 0); }

    /**
     * Register a signal read at most every periodMs (between reads the last value is kept).
     * Use this for slow hardware calls, e.g. motor current (~2 ms per read on a REV hub).
     */
    public Recorder signal(String name, Source source, int periodMs) {
        if (started) throw new IllegalStateException("add signals before start(): " + name);
        if (index.containsKey(name)) throw new IllegalArgumentException("duplicate signal: " + name);
        index.put(name, names.size());
        names.add(name);
        sources.add(source);
        periodNs.add(periodMs * 1_000_000L);
        return this;
    }

    public boolean has(String name) { return index.containsKey(name); }

    /**
     * At most this many rate-limited signals (those with periodMs) are read per update (default 2), so slow
     * hardware reads such as motor current (~2 ms each on a REV hub) are spread over loops instead of all
     * landing in one loop every period.
     */
    public Recorder maxSlowReadsPerUpdate(int n) { maxSlowReads = Math.max(1, n); return this; }

    /** Default log folder: /sdcard/FIRST/asa on the robot, ./asa-logs on a laptop. */
    public static File defaultDir() {
        File first = new File("/sdcard/FIRST");
        return first.isDirectory() ? new File(first, "asa") : new File(System.getProperty("user.dir"), "asa-logs");
    }

    /** Opens session files "<date>_<tag>.csv" and "<date>_<tag>.events.csv" in dir. */
    public Recorder start(File dir, String tag) {
        if (started) return this;
        this.dir = dir;
        this.session = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_" + safe(tag);
        int n = names.size();
        values = new double[n];
        lastReadNs = new long[n];
        sumAbs = new double[n];
        peakAbs = new double[n];
        counts = new long[n];
        period = new long[n];
        readNow = new boolean[n];
        t0Ns = System.nanoTime();
        for (int i = 0; i < n; i++) { values[i] = Double.NaN; period[i] = periodNs.get(i); lastReadNs[i] = t0Ns - period[i] - 1; }

        StringBuilder header = new StringBuilder("t");
        for (String s : names) header.append(',').append(s);
        final String dataHeader = header.toString();
        writer = new Thread(new Runnable() { public void run() { writeLoop(dataHeader); } }, "asa-recorder");
        writer.setDaemon(true);
        writer.start();
        started = true;
        return this;
    }

    // ---------------------------------------------------------------- loop

    private double simTime = Double.NaN;

    /** Seconds since start(), or the last time given to update(double). */
    public double time() { return Double.isNaN(simTime) ? (System.nanoTime() - t0Ns) / 1e9 : simTime; }

    /** Read due signals and queue one CSV row. Call once per loop. */
    public void update() {
        if (!started) return;
        sample(System.nanoTime());
    }

    /** Same, on a given clock (seconds since start): for simulations and replaying logs through new rules. */
    public void update(double timeSeconds) {
        if (!started) return;
        simTime = timeSeconds;
        sample(t0Ns + (long) (timeSeconds * 1e9));
    }

    private void sample(long now) {
        StringBuilder row = new StringBuilder(16 + values.length * 10);
        row.append(fmt((now - t0Ns) / 1e9));
        // rate-limited signals: read the most overdue first, at most maxSlowReads per loop, so with many motors and
        // a slow loop every signal still gets its turn (the period stretches evenly instead of starving the last ones)
        for (int k = 0; k < maxSlowReads; k++) {
            int pick = -1;
            for (int i = 0; i < values.length; i++)
                if (period[i] > 0 && !readNow[i] && now - lastReadNs[i] >= period[i] && (pick < 0 || lastReadNs[i] < lastReadNs[pick])) pick = i;
            if (pick < 0) break;
            readNow[pick] = true;
        }
        for (int i = 0; i < values.length; i++) {
            boolean due = period[i] > 0 ? readNow[i] : true;
            readNow[i] = false;
            if (due) {
                lastReadNs[i] = now;
                double v;
                try { v = sources.get(i).get(); } catch (Throwable e) { v = Double.NaN; }
                values[i] = v;
            }
            double v = values[i];
            if (!Double.isNaN(v)) {
                double a = Math.abs(v);
                sumAbs[i] += a;
                counts[i]++;
                if (a > peakAbs[i]) peakAbs[i] = a;
            }
            row.append(',').append(fmt(v));
        }
        samples++;
        offer(new String[]{"d", row.toString()});
    }

    /** Latest value of a signal, NaN if unknown. */
    public double get(String name) {
        Integer i = index.get(name);
        return i == null || i >= values.length ? Double.NaN : values[i];
    }

    /** Timestamped event, e.g. event("AUTO_STARTED", "") or event("WARNING", "lift stall"). */
    public void event(String type, String detail) {
        if (!started) return;
        String line = fmt(time()) + "," + safe(type) + "," + (detail == null ? "" : detail.replace(',', ';').replace('\n', ' '));
        synchronized (recentEvents) {
            recentEvents.addLast(line);
            if (recentEvents.size() > 200) recentEvents.removeFirst();
        }
        offer(new String[]{"e", line});
    }

    /** Flush and close the files. Safe to call twice. */
    public void stop() {
        if (!started) return;
        started = false;
        // offer, not put: if the writer thread died the queue may be full forever
        try { queue.offer(POISON, 1, java.util.concurrent.TimeUnit.SECONDS); writer.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ---------------------------------------------------------------- read-only views (web UI, rules, summary)

    public List<String> names() { return new ArrayList<String>(names); }
    public double[] snapshot() { return values.clone(); }
    public long samples() { return samples; }
    public long dropped() { return dropped; }
    public double meanAbs(int i) { return counts[i] == 0 ? Double.NaN : sumAbs[i] / counts[i]; }
    public double peakAbs(int i) { return counts[i] == 0 ? Double.NaN : peakAbs[i]; }
    public File dir() { return dir; }
    public String session() { return session; }
    public boolean isStarted() { return started; }
    /** Why the files are not being written, or null when all is well. */
    public String writeError() { return writeError; }

    public List<String> recentEvents() {
        synchronized (recentEvents) { return new ArrayList<String>(recentEvents); }
    }

    // ---------------------------------------------------------------- internals

    private void offer(String[] item) { if (!queue.offer(item)) dropped++; }

    private void writeLoop(String dataHeader) {
        BufferedWriter data = null, events = null;
        try {
            dir.mkdirs();
            data = new BufferedWriter(new FileWriter(new File(dir, session + ".csv")));
            events = new BufferedWriter(new FileWriter(new File(dir, session + ".events.csv")));
            data.write(dataHeader); data.write('\n');
            events.write("t,type,detail\n");
            long lastFlush = System.nanoTime();
            while (true) {
                String[] item = queue.take();
                if (item == POISON) break;
                BufferedWriter w = item[0].equals("d") ? data : events;
                w.write(item[1]); w.write('\n');
                // ponytail: flush every 250 ms, so a power cut loses at most that much
                if (System.nanoTime() - lastFlush > 250_000_000L) { data.flush(); events.flush(); lastFlush = System.nanoTime(); }
            }
        } catch (Exception e) {
            // disk missing/full: keep the robot running; FtcBlackBox.show() puts this on the Driver Hub
            writeError = e.toString();
        } finally {
            close(data);
            close(events);
        }
    }

    private static void close(BufferedWriter w) { if (w != null) try { w.close(); } catch (IOException ignored) {} }

    public static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "";
        if (Math.abs(v) >= 1e6) return Double.toString(v);
        return Double.toString(Math.round(v * 1e4) / 1e4);
    }

    public static String safe(String s) { return s == null ? "" : s.replaceAll("[^A-Za-z0-9_.-]", "_"); }
}
