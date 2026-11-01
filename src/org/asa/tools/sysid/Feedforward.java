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

    /** Highest steady velocity reachable with the given volts (for motion-profile limits). */
    public double maxVelocity(double volts) { return (volts - kS - (model == SysIdMechanism.Model.ELEVATOR ? kG : 0)) / kV; }
}
