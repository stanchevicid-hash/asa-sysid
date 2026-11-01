# ASA SysId for FTC

[![tests](https://github.com/stanchevicid-hash/asa-sysid/actions/workflows/tests.yml/badge.svg)](https://github.com/stanchevicid-hash/asa-sysid/actions/workflows/tests.yml)

**Measure your mechanism, get feedforward constants, and prove they help.** System identification
for FTC lifts, arms, flywheels and slides. It runs on the Control Hub and you drive it from the Driver Hub.

```
DEFINE  →  MEASURE (4 tests)  →  IDENTIFY (kS kG kV kA)  →  USE (Feedforward)  →  VERIFY (PID vs FF+PID)
```

Made by **ASA, FTC team 28284**. MIT license, free for every team.

## What you get

- **4 automatic tests** (quasistatic and dynamic, up and down) with soft limits, limit switches, a power cap,
  and an instant stop if the mechanism moves against the command.
- **A fit you can trust**: an integral-form, two-stage least-squares fit (no noisy differentiation),
  with encoder glitches and standstill velocity offsets rejected.
- **No constants from bad data**: a reversed motor, a jammed mechanism, wrong velocity units, too little
  power or a bad encoder each produce a clear message instead of numbers.
- **Ready-to-paste Java** and a `Feedforward` class.
- **Verification**: alternating moves with PID only vs. feedforward + the same PID, with RMS error,
  overshoot, settling time, final error and peak voltage. This is the evidence for a Control Award.
- **Web UI** at `http://192.168.43.1:8099/` with results, graphs and a "Copy Java" button.
- **FTC Dashboard**: if your team uses it, volts, position, velocity and battery stream there as live graphs
  automatically. It is found at runtime, so there is no dependency to add.

## Install (2 minutes)

**Android Studio:** download `asa-sysid-dropin.zip` from the
[latest release](https://github.com/stanchevicid-hash/asa-sysid/releases/latest) and unzip it at the root of your
FtcRobotController project. The files land in `TeamCode/src/main/java/org/asa/tools/`. Or copy `src/org` there yourself.

**OnBot Java:** create the folders `org/asa/tools/core`, `org/asa/tools/ftc` and `org/asa/tools/sysid`, then upload
the `.java` files from the matching folders in `src/`. The library itself uses plain Java 7 syntax; only the examples use lambdas.

Then copy `src/org/asa/tools/examples/ExampleLiftSysId.java`, rename it, change the hardware names and the units,
and remove `@Disabled`. No Gradle changes and no other dependencies. It works with FTC SDK 9.x through 12.x.

## Describe your mechanism

```java
@TeleOp(name = "ASA SysId Lift", group = "ASA")
public class LiftSysId extends AsaSysIdOpMode {
    static final double IN_PER_TICK = 0.0177;            // spool circumference / ticks per rev

    protected SysIdMechanism mechanism() {
        DcMotorEx lift = hardwareMap.get(DcMotorEx.class, "lift");
        lift.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER); // raw power: SysId must not fight the built-in PID
        lift.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        return SysIdMechanism.elevator("lift")              // .arm(...) or .simple(...)
            .motor(lift::setPower)                          // repeat for every motor of the mechanism
            .position(() -> lift.getCurrentPosition() * IN_PER_TICK)
            .velocity(() -> lift.getVelocity() * IN_PER_TICK)   // SAME unit, per second
            .battery(Ftc.battery(hardwareMap))
            .softLimits(1, 28)                              // keep a margin from the hard stops
            .maxPower(0.7);
    }
}
```

| Model | Equation | Use for |
|---|---|---|
| `simple` | V = kS·sgn(v) + kV·v + kA·a | flywheel, intake, horizontal slide, drive axis |
| `elevator` | V = kG + kS·sgn(v) + kV·v + kA·a | vertical lift |
| `arm` | V = kG·cos θ + kS·sgn(v) + kV·v + kA·a | pivot arm, θ in radians from **horizontal** |

## On the Driver Hub

Select **ASA SysId Lift** and press INIT. The robot does not move during INIT. Press START, then use gamepad 1:

| Button | Action |
|---|---|
| dpad up / down | choose a test |
| **A** | run the selected test (order: QF, QR, DF, DR) |
| **B** | abort (STOP always cuts power too) |
| **X** | analyze and save |
| **RB** | verification: PID only vs feedforward + PID |
| **Y** | clear everything |

Telemetry shows the live position, velocity, voltage and battery, a checklist of the tests, the result, and where
it was saved (`/sdcard/FIRST/asa/sysid/`).

## Use the result

```java
public static final Feedforward LIFT_FF = Feedforward.elevator(0.41, 0.76, 0.117, 0.018);  // pasted

double[] sp = SysIdVerify.profile(goal - start, MAX_VEL, MAX_ACC, t);     // {pos, vel, acc}
double volts = LIFT_FF.calculate(sp[1], sp[2]) + kP * (start + sp[0] - pos);
lift.setPower(Feedforward.toPower(volts, battery));
```

See `src/org/asa/tools/examples/ExampleLiftFeedforward.java`.

## When it says POOR

| Message | What to do |
|---|---|
| `did not move (max X V applied)` | raise `maxPower`, or check for a jam or an unplugged motor |
| `moved against the command` | `setDirection` flips motor **and** encoder. If the mechanism moved the right way, negate position+velocity in code; if it moved the wrong way, reverse the motor |
| `velocity does not match position` | velocity must be in position units **per second** (ticks/s vs inches is the classic mistake) |
| `ignored N glitched samples` | glitches were removed. If you see this often, check the encoder cable |
| `velocity lag N ms measured and compensated` | informational: the hub averages velocity over time; SysId corrected for it |
| `poor fit` | backlash, a slipping belt, or too little travel |

Re-analyze saved data on a laptop: `SysIdRoutine.load(file)` then `new SysIdAnalyzer().analyze(...)`.

## How accurate is it?

`test/SysIdSelfTest.java` simulates mechanisms with known constants at 1 kHz (static friction, hard
stops, battery sag, sensor noise) and requires the constants back:

| Case | Result |
|---|---|
| Elevator, arm, flywheel, 1% velocity noise | kS, kG, kV within 3%, kA within 5% (typically < 1.5%) |
| Same elevator with injected encoder glitches | all constants within 2% |
| **Real REV hub signals**: quantized encoder, hub velocity averaged over 50 ms, 5 ms latency, 8–25 ms loop jitter | lag measured (26 ms) and compensated; every constant within 2% except arm kA (6%) |
| Gearbox backlash, 2.9° at the arm | all constants within 3% |
| Extreme backlash (~115°) on a flywheel | kV, kA fine; kS 10% low (0.02 V) |
| Reversed motor, jammed, too heavy, wrong units | reported, no constants given |
| Verification with the true feedforward | **−89% RMS tracking error**, settles 6/6 vs 0/6 |
| Verification with a zero feedforward (control) | −0.4% (no difference, as it should be) |

In the ASA ftc-sim simulator (an independent Rapier physics model), the flywheel fit was GOOD with R² = 0.9998, and the lift
was GOOD with R² = 1.0000. The lift mass implied by kG and the one implied by kA agree (5.2 vs 5.1 kg). The verification
was **MODEL VALIDATED, −86.6% RMS error**. The raw data is in `sample-logs/`.

## Status

- Verified: compiles against the official FTC SDK **12.0.0** and **9.2.0** (RobotCore + Hardware from Maven),
  the self-test passes, and it ran end to end in a physics simulator.
- **Not yet verified on a physical Control Hub.** If you run it on a real robot, please
  [send your result](https://github.com/stanchevicid-hash/asa-sysid/issues/new?template=robot-result.yml), good or bad.
  Every result makes the next version better.
- Code runs on the Control Hub (or an RC phone). The Driver Hub only shows the OpMode, telemetry and gamepad.

## Tests

```bash
javac --release 8 -d out src/org/asa/tools/core/*.java src/org/asa/tools/sysid/*.java test/SysIdSelfTest.java
java -cp out SysIdSelfTest
```

Shares its core (`Recorder`, `Ftc`, `AsaWeb`) with [asa-blackbox](https://github.com/stanchevicid-hash/asa-blackbox), our flight
recorder. Use both: SysId tells you how the robot *should* behave, and Black Box shows how it *does*.
