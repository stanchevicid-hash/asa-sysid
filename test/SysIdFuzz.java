import org.asa.tools.sysid.SysIdAnalyzer;
import org.asa.tools.sysid.SysIdMechanism;
import org.asa.tools.sysid.SysIdRoutine;
import org.asa.tools.sysid.SysIdRoutine.Test;

import java.util.Random;

/**
 * Monte Carlo check of the promise "never hands out wrong constants": hundreds of random mechanisms (model,
 * constants, travel, power cap, sensor noise, REV hub quantization + lag, glitches, backlash), a quarter of them
 * with a setup fault (reversed motor, wrong velocity units, jammed). Every result must be either
 * GOOD/OK with correct constants, or POOR. A GOOD/OK with wrong constants, or a fault not caught, fails.
 *
 *   java -cp out SysIdFuzz [runs] [seed]
 * Uses SysIdSelfTest's plant (compile both).
 */
public class SysIdFuzz {

    public static void main(String[] args) {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 1;
        Random rnd = new Random(seed);
        int good = 0, poor = 0, wrong = 0, faults = 0, faultsCaught = 0, flagged = 0;
        int only = Integer.getInteger("only", -1);
        for (int run = 0; run < runs; run++) {
            SysIdMechanism.Model model = SysIdMechanism.Model.values()[rnd.nextInt(3)];
            double kS = 0.05 + rnd.nextDouble() * 0.8;
            double kG = model == SysIdMechanism.Model.SIMPLE ? 0 : 0.1 + rnd.nextDouble() * (model == SysIdMechanism.Model.ARM ? 1.5 : 2.5);
            double kV = Math.exp(Math.log(0.005) + rnd.nextDouble() * Math.log(400));   // 0.005 .. 2 V per unit/s
            double tau = 0.03 + rnd.nextDouble() * 0.3;                                   // mechanical time constant
            double kA = kV * tau;
            double maxPower = 0.4 + rnd.nextDouble() * 0.6;
            double vTop = (maxPower * 12 * 0.6 - kS - kG) / kV;                           // speed reachable at ~60% of the cap
            boolean arm = model == SysIdMechanism.Model.ARM;
            double range = arm ? 1.2 + rnd.nextDouble() * 1.4 : Math.max(0.05, Math.abs(vTop)) * (0.6 + rnd.nextDouble() * 2.5);
            double lo = arm ? -0.6 : 0, hi = lo + range;
            int fault = rnd.nextDouble() < 0.25 ? 1 + rnd.nextInt(3) : 0;   // 1 reversed, 2 wrong units, 3 jammed
            int sign = fault == 1 ? -1 : 1;

            // half the runs: a REV hub with 200 .. 20000 encoder ticks over the travel (flywheel: 28 .. 2800 ticks/rev)
            SysIdSelfTest.revTicksPerUnit = !rnd.nextBoolean() ? 0 : model == SysIdMechanism.Model.SIMPLE
                    ? (28 + rnd.nextDouble() * 2800) / (2 * Math.PI) : (200 + rnd.nextDouble() * 20000) / range;
            // play at the encoder: 2..12 deg on a flywheel shaft, 0.3% of the travel on a lift or arm
            SysIdSelfTest.backlash = SysIdSelfTest.revTicksPerUnit == 0 && rnd.nextDouble() < 0.2
                    ? (model == SysIdMechanism.Model.SIMPLE ? Math.toRadians(2 + rnd.nextDouble() * 10) : range * 0.003) : 0;
            SysIdSelfTest.glitchEvery = SysIdSelfTest.revTicksPerUnit == 0 && SysIdSelfTest.backlash == 0 && rnd.nextDouble() < 0.2 ? 120 + rnd.nextInt(200) : 0;
            System.setProperty("noise", Double.toString(rnd.nextDouble() * 0.02));

            boolean jam = fault == 3;
            double plo = model == SysIdMechanism.Model.SIMPLE ? -1e9 : lo, phi = model == SysIdMechanism.Model.SIMPLE ? 1e9 : hi;
            if (jam) { plo = model == SysIdMechanism.Model.SIMPLE ? 0 : lo + 0.3 * range; phi = plo; }   // cannot move at all
            final SysIdSelfTest.Plant plant = new SysIdSelfTest.Plant(kS, kG, kV, kA, plo, phi, arm, sign);
            final double velScale = fault == 2 ? 20 + rnd.nextDouble() * 200 : 1;    // e.g. ticks/s vs inches
            SysIdMechanism m = (model == SysIdMechanism.Model.ELEVATOR ? SysIdMechanism.elevator("m")
                    : arm ? SysIdMechanism.arm("m") : SysIdMechanism.simple("m"))
                    .motor(p -> plant.power = p).position(plant::measuredPos)
                    .velocity(() -> velScale * plant.measuredVel()).battery(plant::battery).maxPower(maxPower);
            if (model != SysIdMechanism.Model.SIMPLE) m.softLimits(lo + 0.05 * range, hi - 0.05 * range);
            else m.timeout(3);

            SysIdRoutine r = new SysIdRoutine(m);
            double now = 0;
            Random jitter = new Random(run);
            for (Test t : Test.values()) {
                plant.pos = model == SysIdMechanism.Model.SIMPLE ? Math.max(plant.lo, Math.min(plant.hi, 0)) : Math.max(plant.lo, Math.min(plant.hi,
                        t.name().endsWith("FORWARD") ? lo + 0.07 * range : hi - 0.07 * range));
                plant.vel = 0;
                for (int i = 0; i < 300; i++) plant.tick();          // at rest; a flywheel spins down
                plant.vel = 0;
                for (int i = 0; i < 100; i++) plant.tick();
                if (!r.start(t)) continue;
                while (r.isRunning()) {
                    int loopMs = SysIdSelfTest.revTicksPerUnit > 0 ? 8 + jitter.nextInt(18) : 10;
                    for (int i = 0; i < loopMs; i++) plant.tick();
                    now += loopMs / 1000.0;
                    r.update(now);
                }
            }
            if (only >= 0 && run != only) continue;
            SysIdAnalyzer.Result res = new SysIdAnalyzer().analyze("m", model, r.data());
            if (only >= 0) {
                for (Test t : Test.values()) {
                    java.util.List<double[]> d = r.data().get(t);
                    if (d == null) { System.out.println(t + ": none"); continue; }
                    double v0 = d.get(0)[1], v1 = d.get(d.size() - 1)[1], p0 = d.get(0)[2], p1 = d.get(d.size() - 1)[2];
                    System.out.printf("%s: %d samples, V %.2f..%.2f, pos %.3f..%.3f%n", t, d.size(), v0, v1, p0, p1);
                    if (Boolean.getBoolean("dump") && t.name().startsWith("DYNAMIC")) for (int i = 0; i < Math.min(16, d.size()); i++)
                        System.out.printf("   t=%.3f V=%.2f pos=%.4f vel=%.3f%n", d.get(i)[0], d.get(i)[1], d.get(i)[2], d.get(i)[3]);
                }
                System.out.println(res);
                if (System.getProperty("save") != null) try { System.out.println("saved " + r.save(new java.io.File(System.getProperty("save")))); } catch (Exception x) { throw new RuntimeException(x); }
            }

            boolean claimsGood = res.quality != SysIdAnalyzer.Quality.POOR;
            String why = null;
            if (fault > 0) {
                faults++;
                if (claimsGood) why = "fault " + fault + " not caught";
                else faultsCaught++;
            } else if (claimsGood) {
                // tolerance: relative, with an absolute floor of 0.05 V for the friction/gravity terms - an FTC motor's
                // own static friction varies more than that between runs, so a finer error is not observable on a robot
                if (Math.abs(res.kS - kS) > Math.max(0.15 * kS, 0.05)) why = "kS " + res.kS + " vs " + kS;
                else if (model != SysIdMechanism.Model.SIMPLE && Math.abs(res.kG - kG) > Math.max(0.08 * kG, 0.05)) why = "kG " + res.kG + " vs " + kG;
                else if (Math.abs(res.kV - kV) > 0.08 * kV) why = "kV " + res.kV + " vs " + kV;
                else if (Math.abs(res.kA - kA) > 0.30 * kA) why = "kA " + res.kA + " vs " + kA;
            }
            // an OK that names the inaccurate constant in its notes is honest ("kA uncertain", "kA approximate")
            if (why != null && fault == 0 && res.quality == SysIdAnalyzer.Quality.OK) {
                String constant = why.substring(0, 2);
                for (String n : res.notes) if (n.startsWith(constant + " ")) { why = null; flagged++; break; }
            }
            if (why != null) {
                wrong++;
                System.out.printf("WRONG run %d %s kS=%.3f kG=%.3f kV=%.4f kA=%.4f maxP=%.2f range=%.3f rev=%.0f backlash=%.4f glitch=%d noise=%s: %s  [%s R2=%.4f %s]%n",
                        run, model, kS, kG, kV, kA, maxPower, range, SysIdSelfTest.revTicksPerUnit, SysIdSelfTest.backlash,
                        SysIdSelfTest.glitchEvery, System.getProperty("noise"), why, res.quality, res.r2, res.notes);
            } else if (fault == 0) {
                if (claimsGood) good++; else poor++;
            }
        }
        SysIdSelfTest.revTicksPerUnit = 0; SysIdSelfTest.backlash = 0; SysIdSelfTest.glitchEvery = 0;
        System.out.printf("%n%d runs: %d healthy mechanisms -> %d correct GOOD/OK, %d OK with the inexact constant named, %d honest POOR; %d faults -> %d caught; %d WRONG%n",
                runs, runs - faults, good, flagged, poor, faults, faultsCaught, wrong);
        System.out.println(wrong == 0 ? "NEVER WRONG" : wrong + " WRONG");
        System.exit(wrong == 0 ? 0 : 1);
    }
}
