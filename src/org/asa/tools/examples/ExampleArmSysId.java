package org.asa.tools.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;

import org.asa.tools.ftc.AsaSysIdOpMode;
import org.asa.tools.ftc.Ftc;
import org.asa.tools.sysid.SysIdMechanism;

/**
 * Pivot arm. Position must be the angle from HORIZONTAL in radians (gravity term is kG*cos(angle)).
 * Start the arm at rest at a known angle (START_ANGLE) so the encoder zero matches.
 */
@TeleOp(name = "ASA SysId Arm (example)", group = "ASA")
@Disabled
public class ExampleArmSysId extends AsaSysIdOpMode {

    static final double RAD_PER_TICK = 2 * Math.PI / (537.7 * 5);   // motor ticks/rev * gear ratio
    static final double START_ANGLE = Math.toRadians(-30);          // arm resting angle at init

    @Override
    protected SysIdMechanism mechanism() {
        final DcMotorEx arm = hardwareMap.get(DcMotorEx.class, "arm");
        arm.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        arm.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        arm.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        return SysIdMechanism.arm("arm")
                .motor(arm::setPower)
                .position(() -> START_ANGLE + arm.getCurrentPosition() * RAD_PER_TICK)
                .velocity(() -> arm.getVelocity() * RAD_PER_TICK)
                .battery(Ftc.battery(hardwareMap))
                .softLimits(Math.toRadians(-25), Math.toRadians(110))
                .maxPower(0.5)
                .rampRate(0.5)              // short travel: ramp slower to collect more quasistatic data
                .stepVoltage(4);
    }
}
