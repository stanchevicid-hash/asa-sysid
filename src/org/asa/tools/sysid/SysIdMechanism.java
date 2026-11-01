package org.asa.tools.sysid;

import org.asa.tools.core.Recorder;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes one mechanism for SysId. Units are yours (inches, meters, radians, ticks):
 * the constants come out in volts per your unit.
 *
 *   SysIdMechanism lift = SysIdMechanism.elevator("lift")
 *       .motor(liftL::setPower).motor(liftR::setPower)
 *       .position(() -> liftL.getCurrentPosition() * IN_PER_TICK)
 *       .velocity(() -> liftL.getVelocity() * IN_PER_TICK)
 *       .battery(Ftc.battery(hardwareMap))
 *       .softLimits(0.5, 30)
 *       .maxPower(0.7);
 */
public class SysIdMechanism {

    /**
     * SIMPLE:   V = kS*sgn(v) + kV*v + kA*a                (flywheel, intake, slide, drive axis)
     * ELEVATOR: V = kG + kS*sgn(v) + kV*v + kA*a           (vertical lift)
     * ARM:      V = kG*cos(angle) + kS*sgn(v) + kV*v + kA*a (pivot; angle 0 = horizontal)
     */
    public enum Model { SIMPLE, ELEVATOR, ARM }

    public interface Output { void setPower(double power); }
    public interface Flag { boolean get() throws Exception; }

    public final String name;
    public final Model model;
    final List<Output> outputs = new ArrayList<Output>();
    Recorder.Source position, velocity, battery, angle;
    double minPos = Double.NEGATIVE_INFINITY, maxPos = Double.POSITIVE_INFINITY;
    Flag forwardStop, reverseStop;
    double maxPower = 0.6;
    double rampVoltsPerSec = 1.0;
    double stepVolts = 6.0;
    double timeoutS = 5.0;

    public static SysIdMechanism simple(String name) { return new SysIdMechanism(name, Model.SIMPLE); }
    public static SysIdMechanism elevator(String name) { return new SysIdMechanism(name, Model.ELEVATOR); }
    public static SysIdMechanism arm(String name) { return new SysIdMechanism(name, Model.ARM); }

    private SysIdMechanism(String name, Model model) { this.name = name; this.model = model; }

    /** Add a motor (call once per motor; all get the same power). Fix directions first. */
    public SysIdMechanism motor(Output o) { outputs.add(o); return this; }
    public SysIdMechanism position(Recorder.Source s) { position = s; return this; }
    /** Velocity in position-units per second. */
    public SysIdMechanism velocity(Recorder.Source s) { velocity = s; return this; }
    /** Battery volts; power = volts / battery. */
    public SysIdMechanism battery(Recorder.Source s) { battery = s; return this; }
    /** ARM only: angle from horizontal in radians. Defaults to position (if position is already radians from horizontal). */
    public SysIdMechanism angle(Recorder.Source s) { angle = s; return this; }

    /** Test stops when position leaves [min, max] in the direction of travel. */
    public SysIdMechanism softLimits(double min, double max) { minPos = min; maxPos = max; return this; }
    /** Limit switches (or any stop condition) per direction; null = none. */
    public SysIdMechanism limitSwitches(Flag forward, Flag reverse) { forwardStop = forward; reverseStop = reverse; return this; }
    /** Absolute cap on motor power during tests (0..1). */
    public SysIdMechanism maxPower(double p) { maxPower = Math.max(0, Math.min(1, p)); return this; }
    /** Quasistatic ramp rate, volts per second (default 1). Lower = more data, needs more travel. */
    public SysIdMechanism rampRate(double voltsPerSec) { rampVoltsPerSec = voltsPerSec; return this; }
    /** Dynamic step size in volts (default 6, still capped by maxPower). */
    public SysIdMechanism stepVoltage(double volts) { stepVolts = volts; return this; }
    /**
     * Max seconds per test (default 5). Quasistatic tests always get at least the time the ramp needs to reach
     * maxPower at 12 V, so a heavy mechanism that only starts moving near the cap still gets measured.
     */
    public SysIdMechanism timeout(double s) { timeoutS = s; return this; }

    double timeout(boolean quasistatic) {
        return quasistatic ? Math.max(timeoutS, maxPower * 12 / rampVoltsPerSec + 1) : timeoutS;
    }

    void check() {
        if (outputs.isEmpty()) throw new IllegalStateException(name + ": no motor()");
        if (position == null || velocity == null || battery == null)
            throw new IllegalStateException(name + ": position(), velocity() and battery() are required");
        if (model == Model.ARM && angle == null) angle = position;
    }
}
