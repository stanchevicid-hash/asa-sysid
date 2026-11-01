package org.asa.tools.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;

import org.asa.tools.ftc.AsaSysIdOpMode;
import org.asa.tools.ftc.Ftc;
import org.asa.tools.sysid.SysIdMechanism;

/**
 * Copy, rename the hardware, measure INCHES_PER_TICK, remove @Disabled.
 * Run QF, QR, DF, DR on the Driver Hub, press X, paste the constants it prints.
 */
@TeleOp(name = "ASA SysId Lift (example)", group = "ASA")
@Disabled
public class ExampleLiftSysId extends AsaSysIdOpMode {

    static final double INCHES_PER_TICK = 0.0177;   // spool circumference / ticks per revolution

    @Override
    protected SysIdMechanism mechanism() {
        final DcMotorEx left = hardwareMap.get(DcMotorEx.class, "liftLeft");
        final DcMotorEx right = hardwareMap.get(DcMotorEx.class, "liftRight");
        right.setDirection(DcMotorSimple.Direction.REVERSE);
        for (DcMotorEx m : new DcMotorEx[]{left, right}) {
            m.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);   // raw power: SysId must not fight the built-in PID
            m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        }
        return SysIdMechanism.elevator("lift")
                .motor(left::setPower)
                .motor(right::setPower)
                .position(() -> left.getCurrentPosition() * INCHES_PER_TICK)
                .velocity(() -> left.getVelocity() * INCHES_PER_TICK)
                .battery(Ftc.battery(hardwareMap))
                .softLimits(1.0, 28.0)      // inches, keep a margin from the hard stops
                .maxPower(0.7)
                .stepVoltage(6);
    }
}
