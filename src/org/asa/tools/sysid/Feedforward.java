package org.asa.tools.sysid;

/**
 * Model-based feedforward from SysId constants. Output is volts; convert with toPower().
 *
 *   double volts = LIFT_FF.calculate(profileVel, profileAcc) + pid.calculate(pos, target);
 *   lift.setPower(Feedforward.toPower(volts, battery));
 */
public final class Feedforward {

    public final SysIdMechanism.Model model;
    public final double kS, kG, kV, kA;
    /** Where the constants came from: "code", or the file load() read them from. */
    public String source = "code";

    public static Feedforward simple(double kS, double kV, double kA) { return new Feedforward(SysIdMechanism.Model.SIMPLE, kS, 0, kV, kA); }
    public static Feedforward elevator(double kS, double kG, double kV, double kA) { return new Feedforward(SysIdMechanism.Model.ELEVATOR, kS, kG, kV, kA); }
    public static Feedforward arm(double kS, double kG, double kV, double kA) { return new Feedforward(SysIdMechanism.Model.ARM, kS, kG, kV, kA); }

    private Feedforward(SysIdMechanism.Model model, double kS, double kG, double kV, double kA) {
        this.model = model; this.kS = kS; this.kG = kG; this.kV = kV; this.kA = kA;
    }

    /** SIMPLE / ELEVATOR. For ARM use calculate(v, a, angle). */
    public double calculate(double velocity, double acceleration) {
        if (model == SysIdMechanism.Model.ARM) throw new IllegalStateException("arm feedforward needs the angle: calculate(v, a, angleRad)");
        return (model == SysIdMechanism.Model.ELEVATOR ? kG : 0) + kS * Math.signum(velocity) + kV * velocity + kA * acceleration;
    }

    /** ARM: angle from horizontal, radians. */
    public double calculate(double velocity, double acceleration, double angleRad) {
        double g = model == SysIdMechanism.Model.ARM ? kG * Math.cos(angleRad) : model == SysIdMechanism.Model.ELEVATOR ? kG : 0;
        return g + kS * Math.signum(velocity) + kV * velocity + kA * acceleration;
    }

    /** Volts to motor power for the current battery, clipped to [-1, 1]. */
    public static double toPower(double volts, double battery) {
        if (!(battery > 1)) battery = 12;
        return Math.max(-1, Math.min(1, volts / battery));
    }

    /** "ELEVATOR 0.41 0.76 0.117 0.018" (model kS kG kV kA), the format of the .ff files. */
    public String toLine() { return model + " " + kS + " " + kG + " " + kV + " " + kA; }

    /** Inverse of toLine(); null if the line is not valid (bad model, non-finite, or kV not positive). */
    public static Feedforward parse(String line) {
        try {
            String[] p = line.trim().split("\\s+");
            Feedforward f = new Feedforward(SysIdMechanism.Model.valueOf(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Double.parseDouble(p[4]));
            for (double v : new double[]{f.kS, f.kG, f.kV, f.kA}) if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            return f.kV > 0 ? f : null;
        } catch (Exception e) { return null; }
    }

    /** Written by the SysId OpMode on X when the result is GOOD or OK. */
    public void save(java.io.File file) throws java.io.IOException {
        java.io.FileWriter w = new java.io.FileWriter(file);
        try { w.write(toLine() + "\n"); } finally { w.close(); }
    }

    /**
     * The constants SysId measured for this mechanism (saved on X as /sdcard/FIRST/asa/sysid/NAME.ff), or the
     * fallback if there is no file, it is unreadable, or its model differs from the fallback's. Re-run SysId and the
     * new constants are used at the next INIT, no copy-paste:
     *   static final Feedforward LIFT_FF = Feedforward.load("lift", Feedforward.elevator(0.41, 0.76, 0.117, 0.018));
     */
    public static Feedforward load(String mechanism, Feedforward fallback) {
        return load(new java.io.File(new java.io.File(org.asa.tools.core.Recorder.defaultDir(), "sysid"),
                org.asa.tools.core.Recorder.safe(mechanism) + ".ff"), fallback);
    }

    public static Feedforward load(java.io.File file, Feedforward fallback) {
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(file));
            String line;
            try { line = r.readLine(); } finally { r.close(); }
            Feedforward f = line == null ? null : parse(line);
            if (f == null || (fallback != null && f.model != fallback.model)) return fallback;
            f.source = file.getPath();
            return f;
        } catch (Exception e) { return fallback; }
    }

    @Override public String toString() {
        return model + " kS=" + kS + (model != SysIdMechanism.Model.SIMPLE ? " kG=" + kG : "") + " kV=" + kV + " kA=" + kA + " (" + source + ")";
    }

    /** Highest steady velocity reachable with the given volts (for motion-profile limits). */
    public double maxVelocity(double volts) { return (volts - kS - (model == SysIdMechanism.Model.ELEVATOR ? kG : 0)) / kV; }
}
