import org.asa.tools.sysid.Feedforward;
import org.asa.tools.sysid.SysIdAnalyzer;
import org.asa.tools.sysid.SysIdMechanism;
import org.asa.tools.sysid.SysIdRoutine;
import org.asa.tools.sysid.SysIdVerify;
import org.asa.tools.sysid.SysIdRoutine.Test;

import java.util.Random;

/**
 * Desktop self-check for SysId, no robot needed:
 *   java -jar ecj.jar -1.8 -nowarn -cp <ftc-sdk-classes> -d out src test/SysIdSelfTest.java
 *   java -cp out SysIdSelfTest
 * SysId must recover the constants of simulated mechanisms, report bad setups, and the verification
 * must show feedforward beating feedback alone (and no difference when the feedforward is zero).
 */
public class SysIdSelfTest {

    static int failures = 0;
    static int glitchEvery = 0;
    /** > 0: model a REV hub: encoder quantized to this many ticks per unit, hub velocity over a 50 ms window, 5 ms read latency, 8-25 ms loop jitter. */
    static double revTicksPerUnit = 0;
    /** > 0: gearbox backlash in position units; the encoder sits on the motor side of the gap. */
    static double backlash = 0;

    /** 1 kHz physics of V = g(pos) + kS sgn(v) + kV v + kA a, with static friction, hard stops and sensor noise. */
    static class Plant {
        final double kS, kG, kV, kA, lo, hi; final boolean arm; final int sign;
        double pos, vel, power;
        final Random rnd = new Random(42);
        Plant(double kS, double kG, double kV, double kA, double lo, double hi, boolean arm, int sign) {
            this.kS = kS; this.kG = kG; this.kV = kV; this.kA = kA; this.lo = lo; this.hi = hi; this.arm = arm; this.sign = sign;
        }
        double battery() { return 12.6 - 0.8 * Math.abs(power); }
        double gravity() { return arm ? kG * Math.cos(pos) : kG; }
        void step(double dt) {
            double volts = sign * power * battery();      // sign=-1 simulates a reversed motor
            double net = volts - gravity();
            if (vel == 0 && Math.abs(net) <= kS) return;   // static friction holds
            double fr = vel != 0 ? kS * Math.signum(vel) : kS * Math.signum(net);
            double a = (net - fr - kV * vel) / kA;
            double nv = vel + a * dt;
            if (vel != 0 && Math.signum(nv) != Math.signum(vel)) nv = 0;   // stick when crossing zero
            vel = nv;
            pos += vel * dt;
            if (pos < lo) { pos = lo; vel = 0; }
            if (pos > hi) { pos = hi; vel = 0; }
        }
        // true position each millisecond, for the hub-style readings
        final double[] hist = new double[256];
        int ms;
        // backlash: the motor (and its encoder) crosses the gap quickly whenever the drive torque changes sign
        double gap, gapRate;
        void tick() {
            step(0.001);
            double drive = sign * power * battery() - (arm ? kG * Math.cos(pos) : kG);
            double target = Math.abs(drive) < 0.05 ? gap : Math.signum(drive) * backlash / 2;
            double before = gap;
            gap += Math.max(-0.001 * 40 * backlash, Math.min(0.001 * 40 * backlash, target - gap));   // crosses in ~25 ms
            gapRate = (gap - before) / 0.001;
            hist[++ms & 255] = pos + gap;
        }
        double ago(int msBack) { return hist[(ms - Math.min(msBack, Math.min(ms, 250))) & 255]; }
        double ticks(double x) { return Math.round(x * revTicksPerUnit); }
        double revPos() { return ticks(ago(5)) / revTicksPerUnit; }
        double revVel() { return (ticks(ago(5)) - ticks(ago(55))) / 0.05 / revTicksPerUnit; }
        int reads;
        double measuredVel() {
            if (revTicksPerUnit > 0) return revVel();
            if (backlash > 0) return vel + gapRate + rnd.nextGaussian() * 0.01 * (1 + Math.abs(vel));
            if (glitchEvery > 0 && ++reads % glitchEvery == 0) return -150;   // one bad velocity read
 return vel + rnd.nextGaussian() * Double.parseDouble(System.getProperty("noise", "0.01")) * (1 + Math.abs(vel)); }
        double measuredPos() { if (revTicksPerUnit > 0) return revPos(); if (backlash > 0) return pos + gap; return pos + rnd.nextGaussian() * 1e-4 + (glitchEvery > 0 && reads % glitchEvery == 7 ? 300 : 0); }
    }

    static SysIdAnalyzer.Result run(SysIdMechanism.Model model, final Plant plant, double minPos, double maxPos, double startLow, double startHigh) {
        SysIdMechanism m = model == SysIdMechanism.Model.ELEVATOR ? SysIdMechanism.elevator("lift")
                : model == SysIdMechanism.Model.ARM ? SysIdMechanism.arm("arm") : SysIdMechanism.simple("flywheel");
        m.motor(p -> plant.power = p)
         .position(plant::measuredPos)
         .velocity(plant::measuredVel)
         .battery(plant::battery)
         .softLimits(minPos, maxPos)
         .maxPower(0.6);
        if (model == SysIdMechanism.Model.SIMPLE) m.timeout(3);
        SysIdRoutine r = new SysIdRoutine(m);
        double now = 0;
        for (Test t : new Test[]{Test.QUASISTATIC_FORWARD, Test.QUASISTATIC_REVERSE, Test.DYNAMIC_FORWARD, Test.DYNAMIC_REVERSE}) {
            // operator moves the mechanism to the start position
            plant.pos = t.name().endsWith("FORWARD") ? startLow : startHigh;
            plant.vel = 0;
            for (int i = 0; i < 100; i++) plant.tick();       // settle so the hub history is at rest
            check(r.start(t), "start " + t + ": " + r.status());
            Random jitter = new Random(7);
            while (r.isRunning()) {
                int loopMs = revTicksPerUnit > 0 ? 8 + jitter.nextInt(18) : 10;   // FTC loops are not regular
                for (int i = 0; i < loopMs; i++) plant.tick();
                now += loopMs / 1000.0;
                r.update(now);
            }
            check(r.has(t), t + " recorded: " + r.status());
        }
        return new SysIdAnalyzer().window(Integer.getInteger("win", 10)).analyze(m.name, model, r.data());
    }

    /** Elevator run where the velocity reading is biased and/or scaled (scale 50 = ticks vs inches mix-up). */
    static SysIdAnalyzer.Result runBias(final Plant plant, final double bias, final double scale) {
        SysIdMechanism m = SysIdMechanism.elevator("bad").motor(p -> plant.power = p)
                .position(plant::measuredPos).velocity(() -> bias + 0.0257 + scale * plant.measuredVel())
                .battery(plant::battery).softLimits(1, 29).maxPower(0.6);
        SysIdRoutine r = new SysIdRoutine(m);
        double now = 0;
        for (Test t : Test.values()) {
            plant.pos = Math.max(plant.lo, Math.min(plant.hi, t.name().endsWith("FORWARD") ? 1.5 : 28.5)); plant.vel = 0;
            r.start(t);
            while (r.isRunning()) { for (int i = 0; i < 10; i++) plant.tick(); now += 0.01; r.update(now); }
        }
        return new SysIdAnalyzer().analyze("bad", SysIdMechanism.Model.ELEVATOR, r.data());
    }

    static void badSetup(String what, SysIdAnalyzer.Result res, String... expect) {
        boolean said = false;
        for (String n : res.notes) for (String x : expect) said |= n.contains(x);
        boolean ok = said && res.quality == SysIdAnalyzer.Quality.POOR;
        String shown = res.notes.isEmpty() ? "(no notes)" : res.notes.get(0);
        for (String n : res.notes) for (String x : expect) if (n.contains(x)) shown = n;
        System.out.println("  " + (ok ? "ok  " : "FAIL") + " " + what + " -> " + (ok ? shown : res.notes + " " + res.quality));
        if (!ok) failures++;
    }

    static void near(String what, double got, double want, double tol) {
        boolean ok = Math.abs(got - want) <= tol * Math.abs(want);
        System.out.printf("  %-4s %-10s got %9.4f want %9.4f  %s%n", ok ? "ok" : "FAIL", what, got, want, ok ? "" : "(>" + (int) (tol * 100) + "%)");
        if (!ok) failures++;
    }

    static void check(boolean ok, String what) {
        if (!ok) { failures++; System.out.println("  FAIL " + what); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("ELEVATOR");
        SysIdAnalyzer.Result e = run(SysIdMechanism.Model.ELEVATOR, new Plant(0.4, 0.8, 0.12, 0.02, 0, 30, false, 1), 1, 29, 1.5, 28.5);
        System.out.println("  " + e.lines());
        near("kS", e.kS, 0.4, 0.03); near("kG", e.kG, 0.8, 0.03); near("kV", e.kV, 0.12, 0.03); near("kA", e.kA, 0.02, 0.05);
        check(e.quality == SysIdAnalyzer.Quality.GOOD, "elevator quality GOOD, got " + e.quality);

        System.out.println("ARM");
        SysIdAnalyzer.Result a = run(SysIdMechanism.Model.ARM, new Plant(0.3, 0.6, 1.0, 0.08, -0.6, 1.5, true, 1), -0.5, 1.4, -0.45, 1.35);
        System.out.println("  " + a.lines());
        near("kS", a.kS, 0.3, 0.03); near("kG", a.kG, 0.6, 0.03); near("kV", a.kV, 1.0, 0.03); near("kA", a.kA, 0.08, 0.05);

        System.out.println("SIMPLE (flywheel, no limits)");
        SysIdAnalyzer.Result s = run(SysIdMechanism.Model.SIMPLE, new Plant(0.2, 0, 0.02, 0.004, -1e9, 1e9, false, 1),
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0);
        System.out.println("  " + s.lines());
        near("kS", s.kS, 0.2, 0.03); near("kV", s.kV, 0.02, 0.03); near("kA", s.kA, 0.004, 0.05);

        System.out.println("REVERSED MOTOR must be reported");
        SysIdAnalyzer.Result rev = run(SysIdMechanism.Model.ELEVATOR, new Plant(0.4, 0.8, 0.12, 0.02, 0, 30, false, -1), 1, 29, 1.5, 28.5);
        boolean flagged = false;
        for (String n : rev.notes) flagged |= n.contains("wrong way");
        check(flagged && rev.quality == SysIdAnalyzer.Quality.POOR, "reversed motor flagged: " + rev.notes + " " + rev.quality);
        System.out.println("  " + (flagged ? "ok   flagged" : "FAIL not flagged"));

        System.out.println("REAL REV HUB SIGNALS: quantized encoder, 50 ms hub velocity, latency, 8-25 ms loop jitter");
        revTicksPerUnit = 56.5;      // 0.0177 in per tick (goBILDA 312 RPM on a 38 mm spool)
        SysIdAnalyzer.Result re = run(SysIdMechanism.Model.ELEVATOR, new Plant(0.4, 0.8, 0.12, 0.02, 0, 30, false, 1), 1, 29, 1.5, 28.5);
        System.out.println("  elevator " + re.lines());
        near("kS", re.kS, 0.4, 0.05); near("kG", re.kG, 0.8, 0.05); near("kV", re.kV, 0.12, 0.05); near("kA", re.kA, 0.02, 0.10);
        revTicksPerUnit = 537.7 * 5 / (2 * Math.PI);   // 312 RPM motor, 5:1 to the arm, radians
        SysIdAnalyzer.Result ra = run(SysIdMechanism.Model.ARM, new Plant(0.3, 0.6, 1.0, 0.08, -0.6, 1.5, true, 1), -0.5, 1.4, -0.45, 1.35);
        System.out.println("  arm " + ra.lines());
        near("kS", ra.kS, 0.3, 0.05); near("kG", ra.kG, 0.6, 0.05); near("kV", ra.kV, 1.0, 0.05); near("kA", ra.kA, 0.08, 0.10);
        revTicksPerUnit = 28 / (2 * Math.PI);          // bare 6000 RPM motor on the flywheel, rad
        SysIdAnalyzer.Result rf = run(SysIdMechanism.Model.SIMPLE, new Plant(0.2, 0, 0.02, 0.004, -1e9, 1e9, false, 1),
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0);
        System.out.println("  flywheel " + rf.lines());
        near("kS", rf.kS, 0.2, 0.05); near("kV", rf.kV, 0.02, 0.05); near("kA", rf.kA, 0.004, 0.10);
        revTicksPerUnit = 0;

        System.out.println("GEARBOX BACKLASH (encoder on the motor side of the gap)");
        backlash = 0.05;             // 0.05 rad = 2.9 deg of play at the arm
        SysIdAnalyzer.Result ba = run(SysIdMechanism.Model.ARM, new Plant(0.3, 0.6, 1.0, 0.08, -0.6, 1.5, true, 1), -0.5, 1.4, -0.45, 1.35);
        System.out.println("  arm " + ba.lines());
        near("kS", ba.kS, 0.3, 0.10); near("kG", ba.kG, 0.6, 0.05); near("kV", ba.kV, 1.0, 0.05); near("kA", ba.kA, 0.08, 0.15);
        backlash = 2.0;              // 2 rad of play on the flywheel shaft
        SysIdAnalyzer.Result bf = run(SysIdMechanism.Model.SIMPLE, new Plant(0.2, 0, 0.02, 0.004, -1e9, 1e9, false, 1),
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0);
        System.out.println("  flywheel " + bf.lines());
        // extreme play (~115 deg): the motor crosses the gap unloaded, which pulls kS a little low (-10%, i.e. 0.02 V)
        near("kS", bf.kS, 0.2, 0.15); near("kV", bf.kV, 0.02, 0.05); near("kA", bf.kA, 0.004, 0.15);
        backlash = 0;

        System.out.println("ENCODER GLITCHES are ignored (position spike + velocity spike, like the sim lift)");
        glitchEvery = 150;
        SysIdAnalyzer.Result gl = run(SysIdMechanism.Model.ELEVATOR, new Plant(0.4, 0.8, 0.12, 0.02, 0, 30, false, 1), 1, 29, 1.5, 28.5);
        glitchEvery = 0;
        System.out.println("  " + gl.lines());
        near("kS", gl.kS, 0.4, 0.03); near("kG", gl.kG, 0.8, 0.03); near("kV", gl.kV, 0.12, 0.03); near("kA", gl.kA, 0.02, 0.05);
        check(gl.ok(), "glitched elevator still usable, got " + gl.quality);

        System.out.println("BAD SETUPS must be reported, never give constants");
        badSetup("jammed lift", runBias(new Plant(0.4, 0.8, 0.12, 0.02, 1.5, 1.5, false, 1), 0, 1), "did not move");
        badSetup("too heavy for maxPower", runBias(new Plant(0.4, 20, 0.12, 0.02, 0, 30, false, 1), 0, 1), "raise maxPower", "needs more voltage");
        badSetup("velocity in wrong units", runBias(new Plant(0.4, 0.8, 0.12, 0.02, 0, 30, false, 1), 0, 50), "position units per second");

        System.out.println("FEEDFORWARD");
        Feedforward ff = e.feedforward();
        check(Math.abs(ff.calculate(10, 0) - (e.kG + e.kS + e.kV * 10)) < 1e-9, "elevator ff formula");
        check(Feedforward.toPower(24, 12) == 1 && Feedforward.toPower(6, 12) == 0.5, "toPower clip");
        check(e.toJava().contains("LIFT_kG") && e.toJava().contains("Feedforward.elevator("), "toJava");
        System.out.println("  ok");

        verify();

        System.out.println(failures == 0 ? "\nALL PASSED" : "\n" + failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static SysIdVerify.Result runVerify(Feedforward ff) {
        final Plant plant = new Plant(0.4, 0.8, 0.12, 0.02, 0, 30, false, 1);
        plant.pos = 0.3;   // resting below the soft limit, like a lift at the bottom after the SysId tests
        SysIdMechanism m = SysIdMechanism.elevator("lift").motor(p -> plant.power = p)
                .position(plant::measuredPos).velocity(plant::measuredVel).battery(plant::battery)
                .softLimits(1, 29).maxPower(0.6);
        // same gains for both runs: what auto() picks for this lift
        SysIdVerify v = new SysIdVerify(m, ff, 8, 22, 1.07, 0.02, 14, 72, 3);
        v.start();
        double now = 0;
        while (v.isRunning() && now < 120) { for (int i = 0; i < 10; i++) plant.tick(); now += 0.01; v.update(now); }
        check(v.status().equals("done"), "verify finished: " + v.status());
        return v.result();
    }

    static void verify() {
        System.out.println("VERIFY (PID only vs feedforward + PID)");
        // profile: reaches the target exactly, continuous, symmetric for negative moves
        double T = SysIdVerify.profileTime(10, 4, 8);
        double[] end = SysIdVerify.profile(10, 4, 8, T + 1), mid = SysIdVerify.profile(10, 4, 8, T / 2), neg = SysIdVerify.profile(-10, 4, 8, T / 2);
        check(Math.abs(end[0] - 10) < 1e-9 && end[1] == 0, "profile ends at target");
        check(Math.abs(mid[0] - 5) < 1e-9 && Math.abs(mid[1] - 4) < 1e-9, "profile midpoint");
        check(Math.abs(neg[0] + 5) < 1e-9 && neg[1] < 0, "negative profile");
        double maxJump = 0;
        for (double t = 0; t < T; t += 0.001) maxJump = Math.max(maxJump, Math.abs(SysIdVerify.profile(10, 4, 8, t + 0.001)[0] - SysIdVerify.profile(10, 4, 8, t)[0]));
        check(maxJump < 4 * 0.001 * 1.01, "profile continuous (no jumps)");

        SysIdVerify.Result good = runVerify(Feedforward.elevator(0.4, 0.8, 0.12, 0.02));
        System.out.println("  " + good.lines());
        check(good.validated() && good.improvement() > 50, "true feedforward must cut RMS error by > 50%, got " + good.improvement());
        SysIdVerify.Result none = runVerify(Feedforward.elevator(0, 0, 0, 0));
        System.out.println("  zero-FF control: RMS change " + String.format("%.1f%%", none.improvement()));
        check(Math.abs(none.improvement()) < 10, "zero feedforward = same controller twice, change must be ~0, got " + none.improvement());
        check(!none.validated() || Math.abs(none.improvement()) < 10, "control");
        System.out.println(failures == 0 ? "  ok" : "");
    }

}
