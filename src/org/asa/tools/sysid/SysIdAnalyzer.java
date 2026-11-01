package org.asa.tools.sysid;

import org.asa.tools.core.Recorder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.asa.tools.sysid.SysIdRoutine.ANG;
import static org.asa.tools.sysid.SysIdRoutine.POS;
import static org.asa.tools.sysid.SysIdRoutine.T;
import static org.asa.tools.sysid.SysIdRoutine.V;
import static org.asa.tools.sysid.SysIdRoutine.VEL;

/**
 * Least-squares fit of the mechanism model to SysId test data.
 * Acceleration = central difference of measured velocity over +-window samples.
 */
public class SysIdAnalyzer {

    public enum Quality { GOOD, OK, POOR }

    public static class Result {
        public final String name;
        public final SysIdMechanism.Model model;
        public double kS, kG, kV, kA, r2 = Double.NaN, rmse = Double.NaN;
        public int used, rejected;
        public Quality quality = Quality.POOR;
        public final List<String> notes = new ArrayList<String>();

        Result(String name, SysIdMechanism.Model model) { this.name = name; this.model = model; }

        public boolean ok() { return quality != Quality.POOR; }

        public Feedforward feedforward() {
            switch (model) {
                case ELEVATOR: return Feedforward.elevator(kS, kG, kV, kA);
                case ARM: return Feedforward.arm(kS, kG, kV, kA);
                default: return Feedforward.simple(kS, kV, kA);
            }
        }

        /** Ready-to-paste Java constants. */
        public String toJava() {
            String p = Recorder.safe(name).toUpperCase(Locale.US);
            StringBuilder s = new StringBuilder();
            s.append("// ASA SysId ").append(model).append(" '").append(name).append("'  R2=").append(f(r2))
             .append("  windows=").append(used).append("  quality=").append(quality).append('\n');
            s.append("public static final double ").append(p).append("_kS = ").append(f(kS)).append(";\n");
            if (model != SysIdMechanism.Model.SIMPLE)
                s.append("public static final double ").append(p).append("_kG = ").append(f(kG)).append(";\n");
            s.append("public static final double ").append(p).append("_kV = ").append(f(kV)).append(";\n");
            s.append("public static final double ").append(p).append("_kA = ").append(f(kA)).append(";\n");
            s.append("public static final Feedforward ").append(p).append("_FF = Feedforward.");
            switch (model) {
                case ELEVATOR: s.append("elevator(").append(p).append("_kS, ").append(p).append("_kG, "); break;
                case ARM: s.append("arm(").append(p).append("_kS, ").append(p).append("_kG, "); break;
                default: s.append("simple(").append(p).append("_kS, ");
            }
            s.append(p).append("_kV, ").append(p).append("_kA);\n");
            return s.toString();
        }

        /** Short lines for the Driver Hub. */
        public List<String> lines() {
            List<String> l = new ArrayList<String>();
            l.add(name + " " + model + "  " + quality + "  R2=" + f(r2));
            l.add("kS=" + f(kS) + (model != SysIdMechanism.Model.SIMPLE ? "  kG=" + f(kG) : "") + "  kV=" + f(kV) + "  kA=" + f(kA));
            l.add("windows " + used + " used, " + rejected + " rejected, rmse " + f(rmse) + " V");
            l.addAll(notes);
            return l;
        }

        @Override public String toString() {
            StringBuilder s = new StringBuilder();
            for (String line : lines()) s.append(line).append('\n');
            return s.append('\n').append(toJava()).toString();
        }

        private static String f(double v) {
            if (Double.isNaN(v)) return "NaN";
            return String.format(Locale.US, Math.abs(v) < 0.01 && v != 0 ? "%.3e" : "%.4f", v);
        }
    }

    private int window = 10;
    private double minVelFraction = 0.03;

    /**
     * Samples per integration window (default 10, i.e. 100 ms at a 10 ms loop). The integral form is exact
     * for any window, so longer only averages more noise; it just needs that many samples of motion.
     */
    public SysIdAnalyzer window(int samples) { window = Math.max(3, samples); return this; }
    /** Windows containing a sample slower than this fraction of the max speed are dropped (default 3%). */
    public SysIdAnalyzer minVelocityFraction(double f) { minVelFraction = f; return this; }

    /**
     * Integral form of the model over each window [t0, t1], divided by its length:
     *   mean(V) = kG*mean(g) + kS*sgn(v) + kV*mean(v) + kA*(v1 - v0)/(t1 - t0)
     * No differentiated acceleration: noise is averaged instead of amplified, and fast transients stay exact.
     */
    public Result analyze(String name, SysIdMechanism.Model model, Map<SysIdRoutine.Test, List<double[]>> data) {
        Result plain = analyze(name, model, data, 0);
        // hub velocity is averaged over a time window, so it lags the position: measure that lag and undo it.
        // The lag is confirmed by the position, so the compensated fit wins unless it is clearly worse
        // (R2 hides this kind of bias, so a tie goes to compensation).
        double lag = velocityLag(data, window);
        if (lag < 0.004) return plain;
        Result comp = analyze(name, model, shift(data, lag), lag);
        boolean worse = comp.quality.ordinal() > plain.quality.ordinal() || comp.r2 < plain.r2 - 0.005
                || (comp.kS < 0 && plain.kS >= 0);
        return worse ? plain : comp;
    }

    private Result analyze(String name, SysIdMechanism.Model model, Map<SysIdRoutine.Test, List<double[]>> data, double lag) {
        Result r = new Result(name, model);
        if (lag > 0) r.notes.add("velocity lag " + Math.round(lag * 1000) + " ms measured and compensated");
        int p = model == SysIdMechanism.Model.SIMPLE ? 3 : 4;
        int w = window;

        for (SysIdRoutine.Test t : SysIdRoutine.Test.values()) {
            List<double[]> d = data.get(t);
            if (d == null || d.size() <= w + 1) r.notes.add("missing test " + t.code);
        }

        double maxVel = 0;
        for (List<double[]> d : data.values()) for (double[] s : d) maxVel = Math.max(maxVel, Math.abs(s[VEL]));
        if (maxVel == 0) { r.notes.add("velocity is always 0: check the velocity source / encoder cable"); return r; }
        double minVel = minVelFraction * maxVel;

        List<double[]> rows = new ArrayList<double[]>();   // regressors..., acceleration last, volts
        List<double[]> quasi = new ArrayList<double[]>(), dyn = new ArrayList<double[]>();
        boolean badData = false;
        for (Map.Entry<SysIdRoutine.Test, List<double[]>> e : data.entrySet()) {
            List<double[]> d = e.getValue();
            String code = e.getKey().code;
            if (d.size() < 2) continue;
            boolean[] glitch = glitches(d);
            int nGlitch = 0;
            for (boolean g : glitch) if (g) nGlitch++;
            if (nGlitch > 0) r.notes.add(code + " ignored " + nGlitch + " glitched samples (position and velocity disagree; check encoder cable if frequent)");
            // the velocity source must integrate to the position change, otherwise the data is meaningless.
            // Only clean steps where the position actually changed: a velocity offset at standstill must not count.
            double dPos = 0, velInt = 0, idleVel = 0, idleT = 0;
            for (int i = 1; i < d.size(); i++) {
                if (glitch[i] || glitch[i - 1]) continue;
                double step = d.get(i)[POS] - d.get(i - 1)[POS];
                if (step == 0) { idleVel += Math.abs(d.get(i)[VEL]) * (d.get(i)[T] - d.get(i - 1)[T]); idleT += d.get(i)[T] - d.get(i - 1)[T]; continue; }
                dPos += step;
                velInt += (d.get(i - 1)[VEL] + d.get(i)[VEL]) / 2 * (d.get(i)[T] - d.get(i - 1)[T]);
            }
            double maxV = 0;
            for (double[] s : d) maxV = Math.max(maxV, Math.abs(s[V]));
            String applied = " (max " + Recorder.fmt(maxV) + " V applied)";
            String bad = null;
            if (dPos == 0 && velInt == 0)
                bad = (idleT > 0 && idleVel / idleT > minVel ? "did not move but velocity reads " + Recorder.fmt(idleVel / idleT) + "/s while still" : "did not move")
                        + applied + ": power too low (raise maxPower), mechanism jammed, or motor unplugged";
            else if (Math.abs(dPos) < 0.2 * Math.abs(velInt))
                bad = "position did not change but velocity reads " + Recorder.fmt(velInt / (d.get(d.size() - 1)[T] - d.get(0)[T]))
                        + "/s" + applied + ": mechanism did not move (needs more voltage? jammed? unplugged?) or velocity is not in position units per second";
            else if (velInt != 0 && Math.signum(velInt) != Math.signum(dPos)) bad = "velocity and position have opposite signs: negate one of them";
            else if (velInt == 0 || Math.abs(velInt / dPos - 1) > 0.5)
                bad = "velocity does not match position (integral of velocity " + Recorder.fmt(velInt) + " vs position change " + Recorder.fmt(dPos)
                        + "): velocity must be in position units per second";
            else if (Math.signum(dPos) != e.getKey().dir) bad = SysIdRoutine.WRONG_WAY;
            else if (nGlitch > 0.1 * d.size()) bad = "too many glitches (" + nGlitch + " of " + d.size() + "): fix the encoder wiring";
            if (bad != null) { r.notes.add(code + " " + bad); badData = true; r.rejected += d.size(); continue; }

            for (int i = 0; i + w < d.size(); i++) {
                double sg = Math.signum(d.get(i)[VEL]);
                double intV = 0, intVel = 0, intG = 0;
                boolean ok = true;
                for (int j = i + 1; j <= i + w && ok; j++) {
                    double[] a = d.get(j - 1), b = d.get(j);
                    double dt = b[T] - a[T];
                    ok = dt > 0 && !glitch[j - 1] && !glitch[j] && Math.abs(b[VEL]) >= minVel && Math.abs(a[VEL]) >= minVel
                            && Math.signum(b[VEL]) == sg && !Double.isNaN(b[V]) && !Double.isNaN(b[VEL]);
                    intV += b[V] * dt;                         // b[V] acted during (a.t, b.t]: zero-order hold
                    intVel += (a[VEL] + b[VEL]) / 2 * dt;
                    intG += model == SysIdMechanism.Model.ARM ? (Math.cos(a[ANG]) + Math.cos(b[ANG])) / 2 * dt : dt;
                }
                double len = d.get(i + w)[T] - d.get(i)[T], disp = d.get(i + w)[POS] - d.get(i)[POS];
                // the position must confirm the motion the velocity claims (rejects standstill velocity offsets)
                ok = ok && Math.signum(disp) == sg && Math.abs(disp) >= 0.5 * Math.abs(intVel) && Math.abs(disp) <= 2 * Math.abs(intVel);
                if (!ok) { r.rejected++; continue; }
                double acc = (d.get(i + w)[VEL] - d.get(i)[VEL]) / len;
                double[] row = new double[p + 2];
                if (model == SysIdMechanism.Model.SIMPLE) { row[0] = sg; row[1] = intVel / len; row[2] = acc; }
                else { row[0] = intG / len; row[1] = sg; row[2] = intVel / len; row[3] = acc; }
                row[p] = intV / len;
                // instrument for kA: the same velocity change from the inner samples, whose noise is independent of acc's
                row[p + 1] = (d.get(i + w - 1)[VEL] - d.get(i + 1)[VEL]) / len;
                rows.add(row);
                (e.getKey().quasistatic ? quasi : dyn).add(row);
            }
        }
        r.used = rows.size();
        if (r.used < 30) { r.notes.add("too few moving windows (" + r.used + "): raise maxPower/stepVoltage, allow more travel or lower window()"); return r; }

        double[] beta = leastSquares(rows, p);
        if (beta == null) { r.notes.add("fit failed: data has no variation (run all 4 tests)"); return r; }
        if (quasi.size() >= 20 && dyn.size() >= 20) beta = twoStage(beta, quasi, dyn, p);
        switch (model) {
            case SIMPLE: r.kS = beta[0]; r.kV = beta[1]; r.kA = beta[2]; break;
            default: r.kG = beta[0]; r.kS = beta[1]; r.kV = beta[2]; r.kA = beta[3];
        }

        double mean = 0;
        for (double[] row : rows) mean += row[p];
        mean /= rows.size();
        double ssRes = 0, ssTot = 0;
        for (double[] row : rows) {
            double pred = 0;
            for (int j = 0; j < p; j++) pred += beta[j] * row[j];
            ssRes += (row[p] - pred) * (row[p] - pred);
            ssTot += (row[p] - mean) * (row[p] - mean);
        }
        r.rmse = Math.sqrt(ssRes / rows.size());
        r.r2 = ssTot > 0 ? 1 - ssRes / ssTot : Double.NaN;

        boolean signsOk = true;
        if (r.kV <= 0) { signsOk = false; r.notes.add("kV <= 0: check units and motor/encoder direction"); }
        if (r.kA < 0) { signsOk = false; r.notes.add("kA < 0: acceleration too noisy; raise window() or step voltage"); }
        if (r.kS < 0) r.notes.add("kS < 0: friction does not fit the model (backlash? slipping belt?)");
        if (r.r2 < 0.85) r.notes.add("poor fit: backlash, slipping belt, wrong units, or too little excitation");

        if (badData) r.notes.add("some tests were left out: fix the notes above and run them again");
        if (!badData && signsOk && r.r2 >= 0.95) r.quality = Quality.GOOD;
        else if (!badData && signsOk && r.r2 >= 0.85) r.quality = Quality.OK;
        else r.quality = Quality.POOR;
        return r;
    }

    /**
     * Delay of the velocity reading relative to the position (0..100 ms): the shift that makes the integral of
     * velocity over each moving window best match the position change (L1, so glitches do not steer it).
     * Returns 0 when the best shift sits at the edge of the search (no clear answer) or barely helps.
     */
    static double velocityLag(Map<SysIdRoutine.Test, List<double[]>> data, int w) {
        double best = 0, bestErr = Double.POSITIVE_INFINITY, err0 = Double.NaN;
        final int steps = 50;
        for (int k = 0; k <= steps; k++) {
            double lag = k * 0.002, err = 0;
            for (List<double[]> d : data.values()) {
                double[] vs = shiftedVel(d, lag);
                for (int i = 0; i + w < d.size(); i += w) {
                    if (Double.isNaN(vs[i + w])) break;
                    double dPos = d.get(i + w)[POS] - d.get(i)[POS];
                    if (dPos == 0 || Math.signum(dPos) != Math.signum(d.get(i)[VEL]) || Math.signum(dPos) != Math.signum(d.get(i + w)[VEL])) continue;   // moving windows only
                    double integral = 0;
                    for (int j = i + 1; j <= i + w; j++) integral += (vs[j - 1] + vs[j]) / 2 * (d.get(j)[T] - d.get(j - 1)[T]);
                    double e = Math.abs(d.get(i + w)[POS] - d.get(i)[POS] - integral);
                    if (!Double.isNaN(e)) err += e;
                }
            }
            if (k == 0) err0 = err;
            if (err < bestErr) { bestErr = err; best = lag; }
        }
        if (best >= steps * 0.002 || !(bestErr < 0.9 * err0)) return 0;
        return best;
    }

    /** Velocity read lag seconds later, i.e. what the sensor reported for each sample time; NaN past the end. */
    private static double[] shiftedVel(List<double[]> d, double lag) {
        double[] out = new double[d.size()];
        int j = 0;
        for (int i = 0; i < d.size(); i++) {
            double t = d.get(i)[T] + lag;
            while (j + 1 < d.size() && d.get(j + 1)[T] < t) j++;
            if (j + 1 >= d.size()) { out[i] = t <= d.get(j)[T] ? d.get(j)[VEL] : Double.NaN; continue; }
            double[] a = d.get(j), b = d.get(j + 1);
            double f = b[T] > a[T] ? Math.max(0, Math.min(1, (t - a[T]) / (b[T] - a[T]))) : 0;
            out[i] = a[VEL] + f * (b[VEL] - a[VEL]);
        }
        return out;
    }

    private static Map<SysIdRoutine.Test, List<double[]>> shift(Map<SysIdRoutine.Test, List<double[]>> data, double lag) {
        Map<SysIdRoutine.Test, List<double[]>> out = new java.util.EnumMap<SysIdRoutine.Test, List<double[]>>(SysIdRoutine.Test.class);
        for (Map.Entry<SysIdRoutine.Test, List<double[]>> e : data.entrySet()) {
            List<double[]> d = e.getValue(), n = new ArrayList<double[]>(d.size());
            double[] vs = shiftedVel(d, lag);
            for (int i = 0; i < d.size(); i++) {
                if (Double.isNaN(vs[i])) break;
                double[] c = d.get(i).clone();
                c[VEL] = vs[i];
                n.add(c);
            }
            out.put(e.getKey(), n);
        }
        return out;
    }

    /**
     * Marks samples where the position step disagrees with the velocity far beyond normal noise
     * (encoder glitch, loose cable, bad read). Threshold: 6x the 90th percentile of the disagreement.
     * Glitches are rare, so they do not move the 90th percentile; ordinary noise, quantization and
     * velocity-filter lag during acceleration all stay below it.
     */
    static boolean[] glitches(List<double[]> d) {
        int n = d.size();
        boolean[] g = new boolean[n];
        if (n < 5) return g;
        double[] err = new double[n - 1];
        for (int i = 1; i < n; i++) {
            double[] a = d.get(i - 1), b = d.get(i);
            err[i - 1] = Math.abs((b[POS] - a[POS]) - (a[VEL] + b[VEL]) / 2 * (b[T] - a[T]));
        }
        double thr = 6 * percentile(err, 0.9) + 1e-12;
        for (int i = 1; i < n; i++)
            if (!(err[i - 1] <= thr)) { g[i - 1] = true; g[i] = true; }   // NaN counts as a glitch too
        return g;
    }

    private static double percentile(double[] x, double q) {
        double[] c = x.clone();
        java.util.Arrays.sort(c);
        return c[Math.min(c.length - 1, (int) (q * c.length))];
    }

    /**
     * A joint fit lets noise in the differentiated acceleration pull kA down and kS up.
     * Instead: static terms (kS, kG, kV) from quasistatic data, where acceleration is small and its
     * kA*a share is moved to the left side; then kA alone from dynamic residuals, as an instrumental-variable
     * estimate (sum res*z / sum acc*z) so velocity noise in acc does not bias kA toward zero. Iterate to converge.
     */
    static double[] twoStage(double[] beta, List<double[]> quasi, List<double[]> dyn, int p) {
        double kA = beta[p - 1];
        double[] b = beta.clone();
        for (int iter = 0; iter < 5; iter++) {
            List<double[]> q = new ArrayList<double[]>(quasi.size());
            for (double[] row : quasi) {
                double[] x = new double[p];
                System.arraycopy(row, 0, x, 0, p - 1);
                x[p - 1] = row[p] - kA * row[p - 1];
                q.add(x);
            }
            double[] st = leastSquares(q, p - 1);
            if (st == null) return beta;   // e.g. quasistatic in one direction only: keep the joint fit
            double num = 0, den = 0;
            for (double[] row : dyn) {
                double res = row[p];
                for (int j = 0; j < p - 1; j++) res -= st[j] * row[j];
                num += res * row[p + 1];
                den += row[p - 1] * row[p + 1];
            }
            if (den == 0) return beta;
            kA = num / den;
            System.arraycopy(st, 0, b, 0, p - 1);
            b[p - 1] = kA;
        }
        return b;
    }

    /** Ordinary least squares via normal equations on RMS-scaled columns; null if singular. */
    static double[] leastSquares(List<double[]> rows, int p) {
        double[] scale = new double[p];
        for (double[] row : rows) for (int j = 0; j < p; j++) scale[j] += row[j] * row[j];
        for (int j = 0; j < p; j++) {
            scale[j] = Math.sqrt(scale[j] / rows.size());
            if (scale[j] == 0) return null;
        }
        double[][] a = new double[p][p + 1];
        for (double[] row : rows)
            for (int i = 0; i < p; i++) {
                double xi = row[i] / scale[i];
                for (int j = 0; j < p; j++) a[i][j] += xi * row[j] / scale[j];
                a[i][p] += xi * row[p];
            }
        // Gaussian elimination with partial pivoting
        for (int c = 0; c < p; c++) {
            int best = c;
            for (int i = c + 1; i < p; i++) if (Math.abs(a[i][c]) > Math.abs(a[best][c])) best = i;
            if (Math.abs(a[best][c]) < 1e-9 * rows.size()) return null;
            double[] tmp = a[c]; a[c] = a[best]; a[best] = tmp;
            for (int i = 0; i < p; i++) {
                if (i == c) continue;
                double f = a[i][c] / a[c][c];
                for (int j = c; j <= p; j++) a[i][j] -= f * a[c][j];
            }
        }
        double[] beta = new double[p];
        for (int j = 0; j < p; j++) beta[j] = a[j][p] / a[j][j] / scale[j];
        return beta;
    }
}
