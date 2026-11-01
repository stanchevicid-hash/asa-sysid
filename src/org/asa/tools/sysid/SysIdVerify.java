package org.asa.tools.sysid;

import org.asa.tools.core.Recorder;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Proves the feedforward helps: the same trapezoid moves (from -> to -> from ...) are driven alternately
 * by feedback only (PD) and by feedforward + the same PD. Alternating cancels battery drain over the run.
 * Non-blocking like SysIdRoutine: start(), then update(now) every loop.
 *
 *   SysIdVerify v = SysIdVerify.auto(mechanism, result.feedforward());   // or new SysIdVerify(...) with your gains
 */
public class SysIdVerify {

    /** Per-mode averages over all moves. Units: your position units, seconds, volts. */
    public static class Stats {
        public double rms, overshoot, settle, steady, peakVolts;
        public int moves, unsettled;
        private double sRms, sOver, sSettle, sSteady;

        void add(double rms, double over, double settle, boolean settled, double steady, double peak) {
            moves++;
            sRms += rms; sOver += over; sSettle += settle; sSteady += steady;
            if (!settled) unsettled++;
            this.rms = sRms / moves; overshoot = sOver / moves; this.settle = sSettle / moves; this.steady = sSteady / moves;
            peakVolts = Math.max(peakVolts, peak);
        }
    }

    public static class Result {
        public final String name;
        public final Stats pid = new Stats(), ff = new Stats();
        public final List<String> notes = new ArrayList<String>();
        Result(String name) { this.name = name; }

        /** RMS tracking error reduction, percent (positive = feedforward is better). */
        public double improvement() { return pid.rms > 0 ? 100 * (pid.rms - ff.rms) / pid.rms : Double.NaN; }

        public boolean validated() { return ff.moves > 0 && pid.moves > 0 && ff.rms < pid.rms && ff.unsettled <= pid.unsettled; }

        public List<String> lines() {
            List<String> l = new ArrayList<String>();
            l.add(name + " VERIFY: " + (validated() ? "MODEL VALIDATED" : "NOT VALIDATED") + "  RMS -" + f(improvement()) + "%");
            l.add("              PID only   FF + PID");
            l.add("RMS error   " + col(pid.rms) + col(ff.rms));
            l.add("overshoot   " + col(pid.overshoot) + col(ff.overshoot));
            l.add("settle s    " + col(pid.settle) + col(ff.settle));
            l.add("final error " + col(pid.steady) + col(ff.steady));
            l.add("peak V      " + col(pid.peakVolts) + col(ff.peakVolts));
            l.add("moves " + pid.moves + " + " + ff.moves + ", not settled " + pid.unsettled + " / " + ff.unsettled);
            l.addAll(notes);
            return l;
        }

        @Override public String toString() {
            StringBuilder s = new StringBuilder();
            for (String line : lines()) s.append(line).append('\n');
            return s.toString();
        }

        private static String f(double v) { return Double.isNaN(v) ? "NaN" : String.format(Locale.US, "%.1f", v); }
        private static String col(double v) { return String.format(Locale.US, "%11.4f", v); }
    }

    private final SysIdMechanism m;
    private final Feedforward model;
    private final double from, to, kP, kD, maxVel, maxAcc, settleS, tolerance;
    private final int repeats;

    private int move = -2;               // -2 idle, -1 going to start, 0.. measured moves
    private double moveT0 = Double.NaN, lastTarget, lastVolts;
    private String status = "ready";
    private int posStrikes;
    private Result result;
    private final StringBuilder csv = new StringBuilder("t,mode,move,setpoint,pos,vel,volts\n");
    private double runT0 = Double.NaN;
    // samples of the current move
    private final List<double[]> cur = new ArrayList<double[]>();   // t-in-move, error, pos, volts

    /**
     * @param from,to   positions inside the soft limits
     * @param kP        volts per position unit of error, kD volts per (unit/s) of velocity error
     * @param maxVel    profile cruise speed, maxAcc profile acceleration (position units)
     * @param repeats   up/down pairs per mode
     */
    public SysIdVerify(SysIdMechanism m, Feedforward ff, double from, double to, double kP, double kD,
                       double maxVel, double maxAcc, int repeats) {
        m.check();
        this.m = m; this.model = ff; this.from = from; this.to = to; this.kP = kP; this.kD = kD;
        this.maxVel = maxVel; this.maxAcc = maxAcc; this.repeats = Math.max(1, repeats);
        this.settleS = 1.0;
        this.tolerance = 0.02 * Math.abs(to - from);
    }

    /**
     * Picks everything from the model and the soft limits: moves between 25% and 75% of the range;
     * of the volts left after holding gravity and friction, cruise uses ~50% and acceleration ~25%
     * (the rest is headroom for feedback); kP = 1.5 V per 5% of range.
     * For a fair comparison pass the gains your team really uses to the constructor instead.
     */
    public static SysIdVerify auto(SysIdMechanism m, Feedforward ff) {
        double range = m.maxPos - m.minPos;
        if (Double.isInfinite(range) || !(range > 0)) throw new IllegalArgumentException(m.name + ": verification needs softLimits()");
        double vCap = m.maxPower * 12 * 0.8;
        double hold = ff.kS + (ff.model == SysIdMechanism.Model.SIMPLE ? 0 : Math.abs(ff.kG));
        double spare = vCap - hold;
        double vel = 0.5 * spare / ff.kV;
        if (!(spare > 0.5) || !(vel > 0)) throw new IllegalArgumentException(m.name + ": too heavy for maxPower to verify (raise maxPower)");
        vel = Math.min(vel, range / 0.8);                         // reach cruise on a half-range move
        double acc = ff.kA > 0 ? 0.25 * spare / ff.kA : vel / 0.2;
        acc = Math.min(acc, vel / 0.1);
        double kP = 1.5 / (0.05 * range);
        return new SysIdVerify(m, ff, m.minPos + 0.25 * range, m.minPos + 0.75 * range, kP, kP * 0.02, vel, acc, 3);
    }

    /** Same positions, gains and profile with another feedforward (e.g. to compare two sets of constants). */
    public SysIdVerify withFeedforward(Feedforward ff) {
        return new SysIdVerify(m, ff, from, to, kP, kD, maxVel, maxAcc, repeats);
    }

    public void start() {
        stopMotors();
        result = new Result(m.name);
        csv.setLength(0);
        csv.append("t,mode,move,setpoint,pos,vel,volts\n");
        move = -1;
        moveT0 = Double.NaN;
        runT0 = Double.NaN;
        posStrikes = 0;
        try { lastTarget = m.position.get(); } catch (Throwable e) { status = "position read failed"; move = -2; return; }
        status = "moving to start";
    }

    public void abort() { if (move > -2) status = "aborted"; move = -2; stopMotors(); }
    public boolean isRunning() { return move > -2; }
    public String status() { return status; }
    public Result result() { return result; }
    public int totalMoves() { return 4 * repeats; }

    /** Call every loop with a monotonic time in seconds. */
    public void update(double now) {
        if (move == -2) return;
        try {
            if (Double.isNaN(moveT0)) moveT0 = now;
            if (Double.isNaN(runT0)) runT0 = now;
            double t = now - moveT0;
            double target = move == -1 ? from : (move % 2 == 0 ? to : from);
            boolean useFF = move == -1 || (move / 2) % 2 == 1;
            double[] sp = profile(target - lastTarget, maxVel, maxAcc, t);
            double setpoint = lastTarget + sp[0];
            double tProfile = profileTime(target - lastTarget, maxVel, maxAcc);

            double battery = m.battery.get(), pos = m.position.get(), vel = m.velocity.get();
            // the mechanism often rests just past a soft limit (e.g. lift at the bottom): only check once the
            // measured moves run, all of which stay between 25% and 75% of the range, with a 5% margin
            double band = 0.05 * (m.maxPos - m.minPos);
            boolean outside = move >= 0 && (pos > m.maxPos + band || pos < m.minPos - band);
            posStrikes = outside ? posStrikes + 1 : 0;
            if (posStrikes >= 2) { status = "stopped: left the soft limits (check kP / direction)"; move = -2; stopMotors(); return; }

            double fb = kP * (setpoint - pos) + kD * (sp[1] - vel);
            double ffV = 0;
            if (useFF) ffV = m.model == SysIdMechanism.Model.ARM ? model.calculate(sp[1], sp[2], m.angle.get()) : model.calculate(sp[1], sp[2]);
            if (!(battery > 1)) battery = 12;
            double power = Math.max(-m.maxPower, Math.min(m.maxPower, (ffV + fb) / battery));
            for (SysIdMechanism.Output o : m.outputs) o.setPower(power);
            lastVolts = power * battery;

            if (move >= 0) {
                cur.add(new double[]{t, setpoint - pos, pos, lastVolts});
                csv.append(Recorder.fmt(now - runT0)).append(useFF ? ",FF," : ",PID,").append(move).append(',')
                   .append(Recorder.fmt(setpoint)).append(',').append(Recorder.fmt(pos)).append(',')
                   .append(Recorder.fmt(vel)).append(',').append(Recorder.fmt(lastVolts)).append('\n');
            }
            if (t < tProfile + settleS) {
                status = (move < 0 ? "moving to start" : (useFF ? "FF+PID" : "PID") + " move " + (move + 1) + "/" + totalMoves());
                return;
            }
            if (move >= 0) finishMove(useFF, target, tProfile);
            lastTarget = target;
            move++;
            moveT0 = Double.NaN;
            cur.clear();
            if (move >= totalMoves()) {
                move = -2;
                stopMotors();
                status = "done";
                if (!result.validated()) result.notes.add("feedforward did not beat feedback alone: re-run SysId, check units, or the gains are too high to show a difference");
            }
        } catch (Throwable e) {
            status = "error: " + e;
            move = -2;
            stopMotors();
        }
    }

    private void finishMove(boolean useFF, double target, double tProfile) {
        double sumSq = 0, over = 0, peak = 0, steady = 0;
        int n = 0, nSteady = 0;
        double dir = Math.signum(target - (move % 2 == 0 ? from : to));
        double settledAt = Double.NaN;
        for (double[] s : cur) {
            if (s[0] <= tProfile) { sumSq += s[1] * s[1]; n++; }
            over = Math.max(over, dir * (s[2] - target));              // past the target in the travel direction
            peak = Math.max(peak, Math.abs(s[3]));
            double err = Math.abs(target - s[2]);
            if (s[0] >= tProfile) {
                if (err > tolerance) settledAt = Double.NaN;
                else if (Double.isNaN(settledAt)) settledAt = s[0];
                if (s[0] >= tProfile + settleS - 0.2) { steady += err; nSteady++; }
            }
        }
        boolean settled = !Double.isNaN(settledAt);
        double settle = settled ? Math.max(0, settledAt - tProfile) : settleS;
        (useFF ? result.ff : result.pid).add(n > 0 ? Math.sqrt(sumSq / n) : 0, Math.max(0, over), settle, settled,
                nSteady > 0 ? steady / nSteady : Double.NaN, peak);
    }

    private void stopMotors() { for (SysIdMechanism.Output o : m.outputs) try { o.setPower(0); } catch (Throwable ignored) {} }

    /** Writes "<date>_verify_<name>.result.txt" and ".csv"; returns the text file. */
    public File save(File dir) throws Exception {
        dir.mkdirs();
        String base = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_verify_" + Recorder.safe(m.name);
        FileWriter w = new FileWriter(new File(dir, base + ".csv"));
        w.write(csv.toString());
        w.close();
        File txt = new File(dir, base + ".result.txt");
        w = new FileWriter(txt);
        w.write(result == null ? "no result\n" : result.toString());
        w.close();
        return txt;
    }

    // ---------------------------------------------------------------- trapezoid profile

    /** {position, velocity, acceleration} at time t of a trapezoid move of signed distance d from rest to rest. */
    public static double[] profile(double d, double vMax, double aMax, double t) {
        double s = Math.signum(d), dist = Math.abs(d);
        if (dist == 0 || t <= 0) return new double[]{0, 0, 0};
        double ta = vMax / aMax, v = vMax;
        if (aMax * ta * ta > dist) { ta = Math.sqrt(dist / aMax); v = aMax * ta; }   // triangle: never reaches vMax
        double tc = (dist - aMax * ta * ta) / v, total = 2 * ta + tc;
        double p, vel, acc;
        if (t < ta) { p = 0.5 * aMax * t * t; vel = aMax * t; acc = aMax; }
        else if (t < ta + tc) { p = 0.5 * aMax * ta * ta + v * (t - ta); vel = v; acc = 0; }
        else if (t < total) { double r = total - t; p = dist - 0.5 * aMax * r * r; vel = aMax * r; acc = -aMax; }
        else { p = dist; vel = 0; acc = 0; }
        return new double[]{s * p, s * vel, s * acc};
    }

    public static double profileTime(double d, double vMax, double aMax) {
        double dist = Math.abs(d);
        if (dist == 0) return 0;
        double ta = vMax / aMax, v = vMax;
        if (aMax * ta * ta > dist) { ta = Math.sqrt(dist / aMax); v = aMax * ta; }
        return 2 * ta + (dist - aMax * ta * ta) / v;
    }
}
