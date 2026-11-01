package com.acmerobotics.dashboard.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;

/** Test double with the same API as FTC Dashboard's TelemetryPacket. */
public class TelemetryPacket {
    public final Map<String, Object> data = new LinkedHashMap<String, Object>();
    public void put(String key, Object value) { data.put(key, value); }
}
