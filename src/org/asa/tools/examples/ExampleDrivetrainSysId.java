package org.asa.tools.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;

import org.asa.tools.ftc.AsaSysIdOpMode;
import org.asa.tools.ftc.Ftc;
import org.asa.tools.sysid.MotorSpec;
import org.asa.tools.sysid.SysIdAnalyzer;
import org.asa.tools.sysid.SysIdMechanism;

import java.util.Locale;

/**
 * Drivetrain SysId (driving straight forward and back) that also estimates the robot's mass.
 * Needs about 3 m of free floor. Change the names, ratio and wheel size, remove @Disabled.
 * Mass: about +/-15% (datasheet constants, roller friction); a bathroom scale is more exact, this is a cross-check.
 */
@TeleOp(name = "ASA SysId Drivetrain (example)", group = "ASA")
@Disabled
public class ExampleDrivetrainSysId extends AsaSysIdOpMode {

    static final double TICKS_PER_REV = 537.7;            // goBILDA 312 rpm
    static final double WHEEL_DIAMETER_M = 0.104;          // goBILDA 104 mm mecanum
    static final double M_PER_TICK = Math.PI * WHEEL_DIAMETER_M / TICKS_PER_REV;
    static final MotorSpec MOTOR = MotorSpec.GOBILDA_5203;

    DcMotorEx[] wheels;

    @Override
    protected SysIdMechanism mechanism() {
        wheels = new DcMotorEx[]{
                hardwareMap.get(DcMotorEx.class, "frontLeft"), hardwareMap.get(DcMotorEx.class, "backLeft"),
                hardwareMap.get(DcMotorEx.class, "frontRight"), hardwareMap.get(DcMotorEx.class, "backRight")};
        wheels[2].setDirection(DcMotorSimple.Direction.REVERSE);
        wheels[3].setDirection(DcMotorSimple.Direction.REVERSE);
        SysIdMechanism m = SysIdMechanism.simple("drive");
        for (final DcMotorEx w : wheels) {
            w.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            w.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            w.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            m.motor(w::setPower);
        }
        return m.position(() -> average(false) * M_PER_TICK)
                .velocity(() -> average(true) * M_PER_TICK)
                .battery(Ftc.battery(hardwareMap))
                .softLimits(-1.2, 1.2)                     // meters from where it starts
                .maxPower(0.6)
                .rampRate(0.5);
    }

    double average(boolean velocity) {
        double s = 0;
        for (DcMotorEx w : wheels) s += velocity ? w.getVelocity() : w.getCurrentPosition();
        return s / wheels.length;
    }

    @Override
    protected String extra(SysIdAnalyzer.Result r) {
        if (!r.ok()) return null;
        return String.format(Locale.US, "robot mass about %.1f kg (+/-15%%)", MOTOR.robotMassKg(wheels.length, r.kV, r.kA));
    }
}
