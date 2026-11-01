package org.asa.tools.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;

import org.asa.tools.core.Recorder;
import org.asa.tools.ftc.Ftc;
import org.asa.tools.sysid.Feedforward;
import org.asa.tools.sysid.SysIdVerify;

/**
 * Using SysId constants in a real controller: a motion profile drives the feedforward,
 * a small P term corrects the rest. dpad up/down moves the lift between two heights.
 */
@TeleOp(name = "ASA Lift Feedforward (example)", group = "ASA")
@Disabled
public class ExampleLiftFeedforward extends LinearOpMode {

    // pasted from the SysId result (X on the SysId OpMode)
    public static final Feedforward LIFT_FF = Feedforward.elevator(0.41, 0.76, 0.117, 0.018);
    static final double INCHES_PER_TICK = 0.0177, KP = 0.35;   // volts per inch of error
    static final double MAX_VEL = 30, MAX_ACC = 80;             // in/s, in/s^2

    @Override
    public void runOpMode() {
        DcMotorEx lift = hardwareMap.get(DcMotorEx.class, "lift");
        lift.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        lift.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        Recorder.Source battery = Ftc.battery(hardwareMap);

        waitForStart();
        double start = 0, goal = 0, t0 = getRuntime();
        while (opModeIsActive()) {
            double pos = lift.getCurrentPosition() * INCHES_PER_TICK;
            double next = gamepad1.dpad_up ? 25 : gamepad1.dpad_down ? 0 : goal;
            if (next != goal) { start = pos; goal = next; t0 = getRuntime(); }

            double[] sp = SysIdVerify.profile(goal - start, MAX_VEL, MAX_ACC, getRuntime() - t0);   // {pos, vel, acc}
            double volts = LIFT_FF.calculate(sp[1], sp[2]) + KP * (start + sp[0] - pos);
            double vBatt;
            try { vBatt = battery.get(); } catch (Exception e) { vBatt = 12; }
            lift.setPower(Feedforward.toPower(volts, vBatt));

            telemetry.addData("lift", "%.1f in -> %.1f", pos, goal);
            telemetry.update();
        }
    }
}
