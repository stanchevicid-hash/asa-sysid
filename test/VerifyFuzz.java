import org.asa.tools.sysid.Feedforward;
import org.asa.tools.sysid.SysIdMechanism;
import org.asa.tools.sysid.SysIdVerify;

import java.util.Random;

/**
 * Monte Carlo for the PID-vs-feedforward verification (RB) on random lifts and arms:
 *  - it always finishes (or refuses up front with a reason) and never drives past the soft limits;
 *  - with a feedforward close to the truth (+/-3%, like a GOOD SysId) it must not be worse than PID alone,
 *    and it must report MODEL VALIDATED;
 *  - with a zero feedforward (same controller twice) the reported change must be ~0.
 *
 *   java -cp out VerifyFuzz [runs] [seed]      (uses SysIdSelfTest's plant)
 */
public class VerifyFuzz {

    public static void main(String[] args) {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 1;
        Random rnd = new Random(seed);
        int done = 0, refused = 0, validated = 0, controlOk = 0, bad = 0;
        for (int run = 0; run < runs; run++) {
            boolean arm = rnd.nextBoolean();
            double kS = 0.05 + rnd.nextDouble() * 0.6, kG = 0.1 + rnd.nextDouble() * (arm ? 1.2 : 2.0);
            // arms: 0.3..5 V per rad/s (2..40 rad/s top speed, real FTC arms); lifts: any unit
            double kV = arm ? 0.3 + rnd.nextDouble() * 4.7 : Math.exp(Math.log(0.01) + rnd.nextDouble() * Math.log(100));
            double kA = kV * (0.03 + rnd.nextDouble() * 0.25);
            double maxPower = 0.5 + rnd.nextDouble() * 0.5;
            double range = arm ? 1.2 + rnd.nextDouble() * 1.4 : (0.5 + rnd.nextDouble() * 2) * (maxPower * 12 * 0.5 - kS - kG) / kV;
            if (!(range > 0)) { refused++; continue; }
            double lo = arm ? -0.6 : 0, hi = lo + range;
            System.setProperty("noise", Double.toString(rnd.nextDouble() * 0.015));
            double err = 0.03;
            Feedforward near = arm
                    ? Feedforward.arm(kS * (1 + err * (2 * rnd.nextDouble() - 1)), kG * (1 + err * (2 * rnd.nextDouble() - 1)), kV * (1 + err * (2 * rnd.nextDouble() - 1)), kA * (1 + err * (2 * rnd.nextDouble() - 1)))
                    : Feedforward.elevator(kS * (1 + err * (2 * rnd.nextDouble() - 1)), kG * (1 + err * (2 * rnd.nextDouble() - 1)), kV * (1 + err * (2 * rnd.nextDouble() - 1)), kA * (1 + err * (2 * rnd.nextDouble() - 1)));
            Feedforward zero = arm ? Feedforward.arm(0, 0, 0, 0) : Feedforward.elevator(0, 0, 0, 0);

            SysIdVerify.Result withFF = null, withZero = null;
            String problem = null;
            for (int pass = 0; pass < 2 && problem == null; pass++) {
                final SysIdSelfTest.Plant plant = new SysIdSelfTest.Plant(kS, kG, kV, kA, lo, hi, arm, 1);
                plant.pos = lo + 0.02 * range;                       // resting at the bottom, like after SysId
                SysIdMechanism m = (arm ? SysIdMechanism.arm("m") : SysIdMechanism.elevator("m"))
                        .motor(p -> plant.power = p).position(plant::measuredPos).velocity(plant::measuredVel)
                        .battery(plant::battery).softLimits(lo + 0.05 * range, hi - 0.05 * range).maxPower(maxPower);
                SysIdVerify v;
                try { v = SysIdVerify.auto(m, near); }               // gains and profile from the (near) model, as on the robot
                catch (IllegalArgumentException e) { refused++; problem = "refused"; break; }
                if (pass == 1) {                                      // same gains/profile, zero feedforward
                    v = rebuild(m, zero, v);
                }
                v.start();
                double now = 0, minPos = Double.POSITIVE_INFINITY, maxPos = Double.NEGATIVE_INFINITY;
                boolean started = false;
                while (v.isRunning() && now < 600) {
                    for (int i = 0; i < 10; i++) plant.tick();
                    now += 0.01;
                    v.update(now);
                    if (v.status().contains("move")) started = true;   // measured moves only
                    if (started) { minPos = Math.min(minPos, plant.pos); maxPos = Math.max(maxPos, plant.pos); }
                }
                double band = 0.05 * range;
                if (!v.status().equals("done")) problem = "did not finish: " + v.status();
                else if (maxPos > hi - 0.05 * range + band + 1e-9 || minPos < lo + 0.05 * range - band - 1e-9) problem = "left the soft limits";
                if (pass == 0) withFF = v.result(); else withZero = v.result();
            }
            if ("refused".equals(problem)) continue;
            if (problem == null) {
                done++;
                if (withFF.ff.rms > withFF.pid.rms * 1.05) problem = "feedforward worse than PID: " + withFF.ff.rms + " vs " + withFF.pid.rms;
                else if (!withFF.validated()) problem = "true feedforward not validated: " + withFF.lines().get(0);
                else if (Math.abs(withZero.improvement()) > 15) problem = "zero feedforward changed RMS by " + withZero.improvement() + "%";
                if (withFF.validated()) validated++;
                if (Math.abs(withZero.improvement()) <= 15) controlOk++;
            }
            if (problem != null) {
                bad++;
                System.out.printf("BAD run %d %s kS=%.3f kG=%.3f kV=%.4f kA=%.4f maxP=%.2f range=%.3f: %s%n",
                        run, arm ? "ARM" : "ELEVATOR", kS, kG, kV, kA, maxPower, range, problem);
            }
        }
        System.out.printf("%n%d runs: %d finished, %d refused up front (too heavy for maxPower); validated %d, zero-FF control ok %d; %d BAD%n",
                runs, done, refused, validated, controlOk, bad);
        System.exit(bad == 0 ? 0 : 1);
    }

    /** The auto() verifier's positions, gains and profile, with another feedforward. */
    static SysIdVerify rebuild(SysIdMechanism m, Feedforward ff, SysIdVerify like) {
        return like.withFeedforward(ff);
    }
}
