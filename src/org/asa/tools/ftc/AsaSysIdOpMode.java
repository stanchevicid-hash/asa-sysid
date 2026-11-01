package org.asa.tools.ftc;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import org.asa.tools.core.Recorder;
import org.asa.tools.sysid.SysIdAnalyzer;
import org.asa.tools.sysid.SysIdMechanism;
import org.asa.tools.sysid.SysIdRoutine;
import org.asa.tools.sysid.SysIdVerify;
import org.asa.tools.sysid.Feedforward;
import org.asa.tools.sysid.SysIdRoutine.Test;

import java.io.File;
import java.io.FileWriter;

/**
 * SysId on the Driver Hub. Subclass it, describe the mechanism, add @TeleOp:
 *
 *   @TeleOp(name = "ASA SysId Lift", group = "ASA")
 *   public class LiftSysId extends AsaSysIdOpMode {
 *       protected SysIdMechanism mechanism() { ... }
 *   }
 *
 * gamepad1: dpad up/down = choose test, A = run, B = abort, X = analyze + save, Y = clear results,
 * right bumper = verification (PID only vs feedforward + PID) with the analyzed constants.
 * Motors only move after START and only while a test runs. STOP always cuts power.
 */
public abstract class AsaSysIdOpMode extends LinearOpMode {

    /** Describe the mechanism (hardwareMap is ready here). */
    protected abstract SysIdMechanism mechanism();

    /** Override to tune the fit (e.g. new SysIdAnalyzer().window(15)). */
    protected SysIdAnalyzer analyzer() { return new SysIdAnalyzer(); }

    /** Override to verify with your own gains/positions: new SysIdVerify(mech, ff, from, to, kP, kD, vel, acc, 3). */
    protected SysIdVerify verifier(SysIdMechanism mech, Feedforward ff) { return SysIdVerify.auto(mech, ff); }

    private final boolean[] last = new boolean[7];

    @Override
    public void runOpMode() {
        SysIdMechanism mech = mechanism();
        SysIdRoutine routine = new SysIdRoutine(mech);
        AsaWeb.start();
        File dir = new File(Recorder.defaultDir(), "sysid");
        Test[] tests = Test.values();
        int sel = 0;
        SysIdAnalyzer.Result result = null;
        String saved = "";
        SysIdVerify verify = null;

        while (!isStarted() && !isStopRequested()) {      // works on every SDK version (opModeInInit is 8.0+)
            telemetry.addLine("ASA SysId: " + mech.name + " (" + mech.model + ")");
            telemetry.addLine("After START: dpad test, A run, B abort");
            telemetry.addLine("X analyze, RB verify, Y clear");
            telemetry.addLine("Keep hands clear. Soft limits stop each test.");
            if (!AsaWeb.url().isEmpty()) telemetry.addLine("Web: " + AsaWeb.url());
            telemetry.update();
            idle();
        }

        resetRuntime();
        try {
            while (opModeIsActive()) {
                double now = getRuntime();   // OpMode clock: real time on the robot, sim time in a simulator
                boolean up = edge(0, gamepad1.dpad_up), down = edge(1, gamepad1.dpad_down);
                boolean a = edge(2, gamepad1.a), b = edge(3, gamepad1.b), x = edge(4, gamepad1.x), y = edge(5, gamepad1.y);
                boolean rb = edge(6, gamepad1.right_bumper);

                boolean wasRunning = routine.isRunning();
                if (verify != null && verify.isRunning()) {
                    if (b) verify.abort();
                    verify.update(now);
                    if (!verify.isRunning()) saved = verify.result() != null && verify.status().equals("done") ? saveVerify(verify, dir) : verify.status();
                } else if (routine.isRunning()) {
                    if (b) routine.abort();
                } else {
                    if (rb) {
                        if (result == null || !result.ok()) saved = "verify needs a GOOD/OK analysis first (X)";
                        else try { verify = verifier(mech, result.feedforward()); verify.start(); }
                        catch (IllegalArgumentException e) { saved = e.getMessage(); verify = null; }
                    }
                    if (up) sel = (sel + tests.length - 1) % tests.length;
                    if (down) sel = (sel + 1) % tests.length;
                    if (a) routine.start(tests[sel]);
                    if (x) {
                        result = analyzer().analyze(mech.name, mech.model, routine.data());
                        saved = save(routine, result, dir);
                    }
                    if (y) { routine = new SysIdRoutine(mech); result = null; saved = ""; }
                }
                routine.update(now);
                if (wasRunning && !routine.isRunning())          // jump to the next test still missing
                    for (int i = 1; i <= tests.length; i++)
                        if (!routine.has(tests[(sel + i) % tests.length])) { sel = (sel + i) % tests.length; break; }

                telemetry.addLine(mech.name + "  " + routine.status());
                for (int i = 0; i < tests.length; i++)
                    telemetry.addLine((i == sel ? "> " : "  ") + (routine.has(tests[i]) ? "[x] " : "[ ] ") + tests[i].code + "  " + tests[i].name().toLowerCase().replace('_', ' '));
                telemetry.addData("V / pos / vel", "%.2f  %.3f  %.3f", routine.lastVolts(), routine.lastPosition(), routine.lastVelocity());
                telemetry.addData("battery", "%.2f V", routine.lastBattery());
                if (verify != null) {
                    telemetry.addLine("verify: " + verify.status());
                    if (!verify.isRunning() && verify.result() != null && verify.status().equals("done"))
                        for (String l : verify.result().lines()) telemetry.addLine(l);
                } else if (result != null) for (String l : result.lines()) telemetry.addLine(l);
                if (!saved.isEmpty()) telemetry.addLine(saved);
                telemetry.update();
                AsaWeb.status(mech.name + ": " + routine.status() + (result != null ? "\n" + result.lines().get(0) + "\n" + result.lines().get(1) : ""));
            }
        } finally {
            routine.stopMotors();
            if (verify != null) verify.abort();
            AsaWeb.status("");
        }
    }

    private static String save(SysIdRoutine routine, SysIdAnalyzer.Result result, File dir) {
        try {
            File csv = routine.save(dir);
            File txt = new File(dir, csv.getName().replace(".csv", ".result.txt"));
            FileWriter w = new FileWriter(txt);
            w.write(result.toString());
            w.close();
            return "saved " + txt.getPath();
        } catch (Exception e) {
            return "save failed: " + e.getMessage();
        }
    }

    private static String saveVerify(SysIdVerify v, File dir) {
        try { return "saved " + v.save(dir).getPath(); } catch (Exception e) { return "save failed: " + e.getMessage(); }
    }

    private boolean edge(int i, boolean now) {
        boolean r = now && !last[i];
        last[i] = now;
        return r;
    }
}
