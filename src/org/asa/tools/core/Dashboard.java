package org.asa.tools.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Sends values to FTC Dashboard (acmerobotics) when the team has it installed, so they show up as live graphs
 * there. Found by reflection: no compile dependency, and without Dashboard every call is a cheap no-op.
 */
public final class Dashboard {

    private Dashboard() {}

    private static boolean tried;
    private static Object dashboard;
    private static Constructor<?> newPacket;
    private static Method put, send;
    private static long lastNs;

    /** True when FTC Dashboard is on the classpath and running. */
    public static synchronized boolean available() {
        if (!tried) {
            tried = true;
            try {
                Class<?> dash = Class.forName("com.acmerobotics.dashboard.FtcDashboard");
                Class<?> packet = Class.forName("com.acmerobotics.dashboard.telemetry.TelemetryPacket");
                Object instance = dash.getMethod("getInstance").invoke(null);
                if (instance != null) {
                    newPacket = packet.getConstructor();
                    put = packet.getMethod("put", String.class, Object.class);
                    send = dash.getMethod("sendTelemetryPacket", packet);
                    dashboard = instance;
                }
            } catch (Throwable ignored) {
                dashboard = null;   // not installed, or a different API: stay silent
            }
        }
        return dashboard != null;
    }

    /**
     * One packet with the named values (NaN skipped) and optional text lines, at most every minPeriodMs.
     * Never throws.
     */
    public static synchronized void send(List<String> names, double[] values, List<String> lines, int minPeriodMs) {
        if (!available()) return;
        long now = System.nanoTime();
        if (now - lastNs < minPeriodMs * 1_000_000L) return;
        lastNs = now;
        try {
            Object p = newPacket.newInstance();
            int n = Math.min(names.size(), values.length);
            for (int i = 0; i < n; i++) if (!Double.isNaN(values[i])) put.invoke(p, names.get(i), values[i]);
            if (lines != null) for (int i = 0; i < lines.size(); i++) put.invoke(p, "msg" + i, lines.get(i));
            send.invoke(dashboard, p);
        } catch (Throwable e) {
            dashboard = null;   // something changed under us: stop trying instead of failing every loop
        }
    }
}
