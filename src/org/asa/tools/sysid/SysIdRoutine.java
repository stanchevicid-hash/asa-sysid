package org.asa.tools.sysid;

import org.asa.tools.core.Recorder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs the four SysId tests on a mechanism, non-blocking: call update(now) every loop.
 * Recommended order for a lift: QF (up), QR (down), DF (up), DR (down).
 */
public class SysIdRoutine {

    public enum Test {
        QUASISTATIC_FORWARD("QF", true, 1), QUASISTATIC_REVERSE("QR", true, -1),
        DYNAMIC_FORWARD("DF", false, 1), DYNAMIC_REVERSE("DR", false, -1);
        public final String code; final boolean quasistatic; final int dir;
        Test(String code, boolean q, int dir) { this.code = code; quasistatic = q; this.dir = dir; }
    }

    /** One sample: t since test start, applied volts, position, velocity, angle. */
    static final int T = 0, V = 1, POS = 2, VEL = 3, ANG = 4;

    private final SysIdMechanism m;
    private final Map<Test, List<double[]>> data = new EnumMap<Test, List<double[]>>(Test.class);
    private Test running;
    private double startT = Double.NaN, startPos = Double.NaN;
    private int posStrikes;
    private final double[] firstPos = new double[3];
    private int firstN;
    private String status = "ready";
    private double lastVolts, lastPos, lastVel, lastBattery;

    public SysIdRoutine(SysIdMechanism m) { m.check(); this.m = m; }

    public SysIdMechanism mechanism() { return m; }

    /** Start a test; refuses if the mechanism is already at the limit in that direction. */
    public boolean start(Test t) {
        stopMotors();
        try {
            double p = m.position.get();
            if (atLimit(t.dir, p)) {
                status = t.code + ": at the " + (t.dir > 0 ? "top" : "bottom") + " limit, move it " + (t.dir > 0 ? "down" : "up") + " with the left stick first";
                return false;
            }
        } catch (Throwable e) { status = "position read failed: " + e; return false; }
        running = t;
        startT = Double.NaN;
        startPos = Double.NaN;
        posStrikes = 0;
        firstN = 0;
        data.put(t, new ArrayList<double[]>());
        status = t.code + " running";
        return true;
    }

    /**
     * Manual move between tests (e.g. left stick): power is capped at maxPower (a heavy lift needs it just to
     * hold) and cut at the soft limits in the direction of travel. Ignored while a test runs.
     */
    public void jog(double power) {
        if (running != null) return;
        double p = Math.max(-m.maxPower, Math.min(m.maxPower, power));
        try { if (p != 0 && atLimit(p > 0 ? 1 : -1, m.position.get())) p = 0; } catch (Throwable e) { p = 0; }
        setPower(p);
    }

    public void abort() { if (running != null) status = running.code + " aborted"; running = null; stopMotors(); }

    public boolean isRunning() { return running != null; }
    public Test running() { return running; }
    public boolean has(Test t) { List<double[]> d = data.get(t); return d != null && d.size() > 10; }
    public String status() { return status; }
    public double lastVolts() { return lastVolts; }
    public double lastPosition() { return lastPos; }
    public double lastVelocity() { return lastVel; }
    public double lastBattery() { return lastBattery; }
    public Map<Test, List<double[]>> data() { return data; }

    /** Call every loop with a monotonic time in seconds. Between tests it only refreshes the live readings. */
    public void update(double now) {
        if (running == null) {
            try { lastPos = m.position.get(); lastVel = m.velocity.get(); lastBattery = m.battery.get(); } catch (Throwable ignored) {}
            return;
        }
        try {
            if (Double.isNaN(startT)) startT = now;
            double t = now - startT;
            double battery = m.battery.get();
            double pos = m.position.get(), vel = m.velocity.get();
            double ang = m.model == SysIdMechanism.Model.ARM ? m.angle.get() : 0;
            lastPos = pos; lastVel = vel; lastBattery = battery;

            if (firstN < 3) {                // start position = median of 3 reads, so a glitch on the first read does no harm
                firstPos[firstN++] = pos;
                if (firstN == 3) startPos = Math.max(Math.min(firstPos[0], firstPos[1]), Math.min(Math.max(firstPos[0], firstPos[1]), firstPos[2]));
            }
            String stop = null;
            if (t > m.timeout(running.quasistatic)) stop = "timeout";
            else if (switchHit(running.dir)) stop = "limit switch";
            else {
                // position limits need 2 reads in a row: one encoder glitch must not end the test (costs <= 1 loop of travel)
                boolean soft = softLimit(running.dir, pos), wrong = wrongWay(pos);
                posStrikes = soft || wrong ? posStrikes + 1 : 0;
                if (posStrikes >= 2) stop = soft ? "limit" : WRONG_WAY;
            }
            if (stop != null) { finish(stop); return; }

            double want = running.dir * (running.quasistatic ? m.rampVoltsPerSec * t : m.stepVolts);
            if (!(battery > 1)) battery = 12;                     // bad sensor: assume nominal, power cap still holds
            double power = Math.max(-m.maxPower, Math.min(m.maxPower, want / battery));
            // pair this measurement with the voltage that acted during the last interval, not the one we set now
            data.get(running).add(new double[]{t, lastVolts, pos, vel, ang});
            setPower(power);
            lastVolts = power * battery;
        } catch (Throwable e) {
            status = "error: " + e;
            running = null;
            stopMotors();
        }
    }

    private void finish(String why) {
        int n = data.get(running).size();
        status = running.code + " done (" + why + ", " + n + " samples)";
        running = null;
        stopMotors();
    }

    static final String WRONG_WAY = "moved against the command. If it physically moved the right way, negate position and velocity "
            + "in code (setDirection flips motor AND encoder); if it moved the wrong way, reverse the motor; "
            + "if gravity pulled it back, raise maxPower";

    /** Safety: travelling against the command by 10% of the range means motor/encoder signs disagree; stop before the hard stop. */
    private boolean wrongWay(double pos) {
        double range = m.maxPos - m.minPos;
        return !Double.isNaN(startPos) && !Double.isInfinite(range) && (pos - startPos) * running.dir < -0.1 * range;
    }

    private boolean atLimit(int dir, double pos) throws Exception { return softLimit(dir, pos) || switchHit(dir); }

    private boolean softLimit(int dir, double pos) { return dir > 0 ? pos >= m.maxPos : pos <= m.minPos; }

    private boolean switchHit(int dir) throws Exception {
        SysIdMechanism.Flag f = dir > 0 ? m.forwardStop : m.reverseStop;
        return f != null && f.get();
    }

    private void setPower(double p) { for (SysIdMechanism.Output o : m.outputs) o.setPower(p); }

    public void stopMotors() {
        for (SysIdMechanism.Output o : m.outputs) try { o.setPower(0); } catch (Throwable ignored) {}
        lastVolts = 0;
    }

    // ---------------------------------------------------------------- persistence

    /** Writes "<date>_sysid_<name>.csv" (test,t,volts,pos,vel,angle) and returns the file. */
    public File save(File dir) throws Exception {
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File f = new File(dir, stamp + "_sysid_" + Recorder.safe(m.name) + ".csv");
        FileWriter w = new FileWriter(f);
        w.write("test,t,volts,pos,vel,angle\n");
        for (Map.Entry<Test, List<double[]>> e : data.entrySet())
            for (double[] s : e.getValue())
                w.write(e.getKey().code + "," + s[T] + "," + s[V] + "," + s[POS] + "," + s[VEL] + "," + s[ANG] + "\n");
        w.close();
        return f;
    }

    /** Reads a file written by save(), e.g. to re-analyze on a laptop. */
    public static Map<Test, List<double[]>> load(File f) throws Exception {
        Map<Test, List<double[]>> d = new EnumMap<Test, List<double[]>>(Test.class);
        BufferedReader r = new BufferedReader(new FileReader(f));
        r.readLine();
        for (String line; (line = r.readLine()) != null; ) {
            String[] p = line.split(",");
            if (p.length < 6) continue;
            Test t = null;
            for (Test x : Test.values()) if (x.code.equals(p[0])) t = x;
            if (t == null) continue;
            if (!d.containsKey(t)) d.put(t, new ArrayList<double[]>());
            d.get(t).add(new double[]{Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Double.parseDouble(p[4]), Double.parseDouble(p[5])});
        }
        r.close();
        return d;
    }
}
