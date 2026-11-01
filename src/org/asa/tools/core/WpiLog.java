package org.asa.tools.core;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Converts a Black Box session (.csv + .events.csv) into a WPILOG file that AdvantageScope opens directly.
 * Signals become double entries under "ASA/" ("lift.amps" -> "ASA/lift/amps"), events a string entry "ASA/events".
 * Streams row by row, so a long session never has to fit in memory.
 *
 *   java -cp asa.jar org.asa.tools.core.WpiLog 20261101_134141_teleop.csv      (writes .wpilog next to it)
 */
public final class WpiLog {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final OutputStream out;

    private WpiLog(OutputStream out) { this.out = out; }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) { System.out.println("usage: WpiLog <session.csv> [out.wpilog]"); return; }
        File csv = new File(args[0]);
        File dst = new File(args.length > 1 ? args[1] : args[0].replaceAll("\\.csv$", "") + ".wpilog");
        OutputStream o = new BufferedOutputStream(new FileOutputStream(dst));
        try { convert(csv, o); } finally { o.close(); }
        System.out.println("wrote " + dst);
    }

    /** Writes the WPILOG for this session CSV (and its .events.csv if present) to out; does not close out. */
    public static void convert(File csv, OutputStream out) throws IOException {
        WpiLog w = new WpiLog(out);
        out.write("WPILOG".getBytes(UTF8));
        out.write(new byte[]{0x00, 0x01});                    // version 1.0, little-endian 0x0100
        byte[] extra = "ASA Black Box".getBytes(UTF8);
        w.u32(extra.length);
        out.write(extra);

        BufferedReader r = new BufferedReader(new FileReader(csv));
        try {
            String header = r.readLine();
            if (header == null || !header.startsWith("t,")) throw new IOException("not a Black Box session csv: " + csv);
            String[] names = header.split(",", -1);
            for (int i = 1; i < names.length; i++) w.start(i, "ASA/" + names[i].replace('.', '/'), "double");
            int eventsId = names.length;
            w.start(eventsId, "ASA/events", "string");

            for (String line; (line = r.readLine()) != null; ) {
                String[] p = line.split(",", -1);
                long ts;
                try { ts = Math.round(Double.parseDouble(p[0]) * 1e6); } catch (Exception e) { continue; }
                for (int i = 1; i < p.length && i < names.length; i++) {
                    if (p[i].isEmpty()) continue;                // NaN in the csv: no record
                    double v;
                    try { v = Double.parseDouble(p[i]); } catch (Exception e) { continue; }
                    long bits = Double.doubleToLongBits(v);
                    byte[] b = new byte[8];
                    for (int k = 0; k < 8; k++) b[k] = (byte) (bits >>> (8 * k));
                    w.record(i, ts, b);
                }
            }

            File ev = new File(csv.getPath().replaceAll("\\.csv$", "") + ".events.csv");
            if (ev.isFile()) {
                BufferedReader e = new BufferedReader(new FileReader(ev));
                try {
                    e.readLine();
                    for (String line; (line = e.readLine()) != null; ) {
                        String[] p = line.split(",", 3);
                        if (p.length < 2) continue;
                        long ts;
                        try { ts = Math.round(Double.parseDouble(p[0]) * 1e6); } catch (Exception x) { continue; }
                        w.record(eventsId, ts, (p[1] + (p.length > 2 && !p[2].isEmpty() ? " " + p[2] : "")).getBytes(UTF8));
                    }
                } finally { e.close(); }
            }
        } finally { r.close(); }
        out.flush();
    }

    /** Control record "start": announces entry id, name and type. */
    private void start(int id, String name, String type) throws IOException {
        byte[] n = name.getBytes(UTF8), t = type.getBytes(UTF8);
        byte[] p = new byte[1 + 4 + 4 + n.length + 4 + t.length + 4];
        int k = 0;
        p[k++] = 0;                                            // control type 0 = start
        k = le(p, k, id, 4);
        k = le(p, k, n.length, 4); System.arraycopy(n, 0, p, k, n.length); k += n.length;
        k = le(p, k, t.length, 4); System.arraycopy(t, 0, p, k, t.length); k += t.length;
        le(p, k, 0, 4);                                        // empty metadata
        record(0, 0, p);
    }

    /** Record with fixed-width fields: 4-byte entry id, 4-byte payload size, 8-byte timestamp (us). */
    private void record(int id, long timestampUs, byte[] payload) throws IOException {
        out.write(0x7F);                                       // (id len-1 = 3) | (size len-1 = 3) << 2 | (ts len-1 = 7) << 4
        u32(id);
        u32(payload.length);
        for (int k = 0; k < 8; k++) out.write((int) (timestampUs >>> (8 * k)) & 0xFF);
        out.write(payload);
    }

    private void u32(long v) throws IOException { for (int k = 0; k < 4; k++) out.write((int) (v >>> (8 * k)) & 0xFF); }

    private static int le(byte[] b, int at, long v, int n) {
        for (int k = 0; k < n; k++) b[at + k] = (byte) (v >>> (8 * k));
        return at + n;
    }
}
