package com.acmerobotics.dashboard;

import com.acmerobotics.dashboard.telemetry.TelemetryPacket;

/** Test double with the same API as FTC Dashboard: records what it is sent. */
public class FtcDashboard {
    private static final FtcDashboard INSTANCE = new FtcDashboard();
    public static TelemetryPacket last;
    public static int packets;
    public static FtcDashboard getInstance() { return INSTANCE; }
    public void sendTelemetryPacket(TelemetryPacket p) { last = p; packets++; }
}
