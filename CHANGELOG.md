# Changelog

## 1.4.1 (2026-11-16)

First run on a real robot (ASA's drivebase, Control Hub, Robot Controller 11.1, OnBot Java). Found there and fixed:

- **Results page in plain words.** Open a session and it says what is fine and what to check, with traffic lights and
  what to do (a motor without current, a wheel drawing more current than the others, encoders not plugged in, battery,
  hub, loop time, every warning explained). Graphs moved underneath. English by default, Romanian and Russian.
- The Driver Hub now tells the driver where the results are (robot Wi-Fi + address).
- **False alarm fixed:** pressing STOP released the gamepads a few ms before the OpMode ended, which was reported as
  "gamepad1 disconnected". A disconnect now has to last 0.25 s.
- ASA Health Check: **Y tests every motor by itself** and shows the report; B stops. It now puts motors in
  RUN_WITHOUT_ENCODER first (a motor left in RUN_TO_POSITION by another OpMode held still and looked like a dead
  encoder), says "NO CURRENT" for a motor that gets no power, and shows the buttons on screen.
- Session comparison no longer reports "+27310%" for a motor that earlier sessions never used.
- New `asa-blackbox-onbot.zip`: every file in one folder and package, for OnBot Java.

## 1.4.0 (2026-11-14)

- **Full Russian guide** in both repos: [GUIDE.ru.md](GUIDE.ru.md) (install, every Driver Hub button, every warning
  and what to do, how it works inside).
- Black Box notices more, with no extra code (`attachAll`):
  - **motor overheating**: RMS current over ~2 minutes above 5 A (first-order thermal model)
  - **drivetrain wheel dragging**: a wheel turning with the others at the same power but drawing 2x their current
  - **frozen IMU** (hung I2C), **loop pause** (one loop over 150 ms), **low battery at INIT** (under 12.4 V idle)
  - records the IMU heading, distance sensors, analog inputs and touch sensors
  - `FtcBlackBox.attachGamepads(bb, gamepad1, gamepad2)`: drivers' sticks on the timeline, warning when a gamepad drops
- ASA Health Check shows each hub's **firmware version**.
- Fixed: a motor **without an encoder** always reads speed 0, so a long spin-up could look like a stall (1 false
  alarm in 3000 simulated matches in 1.3.0). It now needs 1 s instead of 0.5 s.
- SysId: **constants load themselves**. X saves `/sdcard/FIRST/asa/sysid/<name>.ff` (GOOD/OK only) and
  `Feedforward.load("lift", fallback)` uses it at the next INIT, no copy-paste.
- Results: Black Box caught 10163 of 10165 injected faults in 9000 simulated matches (3 seeds) with **0 false alarms**, the new detectors included; the 2 misses were 0.6 s stalls, at the detection limit. Each new detector is checked against its fault and a healthy look-alike (`BlackBoxDetectorsTest`). SysId Monte Carlo: never wrong; verification 200/200; soak 0 drops. Compiles against FTC SDK 12.0.0 and 9.2.0.

## 1.3.0 (2026-11-14)

- Black Box: **hub resets** (every encoder of a hub back to 0 at once) and **hubs not responding** are reported;
  `bb.encodersReset()` marks resets your code does on purpose. Hub temperature is logged.
- Black Box: **battery health**: internal resistance of battery + wiring from normal driving (median of voltage/current
  steps), tracked per battery in `batteries.csv` with `bb.battery("A")`; the report flags a battery worse than usual.
  The self-test recovers 0.08 / 0.12 / 0.30 ohm within 1%.
- Black Box: **loop profiler** (`bb.section`, `bb.profile`) and **crash capture** (`bb.crash(e)`).
- New Driver Hub programs, no code needed: **ASA Health Check** (hubs, batteries, motor-by-motor encoder check) and
  **ASA Center of Gravity** (balance method with the IMU: height, position, tipping limits).
- SysId: **robot mass** from a drivetrain fit (`MotorSpec.robotMassKg`), new `ExampleDrivetrainSysId`.

## 1.2.0 (2026-11-01)

Found by new Monte Carlo tests (8400 random mechanisms for SysId, 1000 for the verification, 3000 full matches for
Black Box) and a soak test:

- Black Box: with many motors and a slow loop, the 100 ms current reads always served the first motors, so the last
  ones could go 0.5 s without a fresh reading. A held value then caused false stall warnings. Rate-limited signals
  are now read most-overdue-first, and every one gets its turn.
- Black Box: the stall rule now needs 0.5 s (a normal spin-up draws stall-like current for 0.1-0.3 s) and resets on
  a direction change, so back-to-back reversals never add up to a stall.
- Black Box: the encoder-lost rule no longer fires on a motor holding a load (encoder still, current ~power x 9 A);
  it needs the low current of a motor spinning free.
- Black Box: `update(time)` replays a session on a given clock (simulations, running old logs through new rules).
- SysId: gearbox play in a dynamic step (the unloaded crossing, then a speed dip) is detected and skipped, which
  removes a kA bias of +30-40%. The search is limited to the first 0.3 s, so noise at top speed is never taken for it.
- SysId: glitched samples no longer steer the velocity-lag estimate.
- SysId: `SysIdVerify.withFeedforward(ff)` compares two sets of constants with the same gains and profile.
- AdvantageScope export writes a value only when it changes: a real session went from 4.3 MB to 0.52 MB, with the
  same data (checked with the WPILib reader).
- Results: Black Box caught 3364 of 3365 injected faults with 0 false alarms in 3000 matches; the only miss was a
  0.7 s stall, at the detection limit. SysId: every one of 2103 setup faults caught; 6 of 6297 healthy mechanisms
  (0.1%) got one constant outside tolerance without a warning, all in extreme setups. Verification: 1000/1000.
  The soak test wrote 60000 rows with none dropped and flat memory.

## 1.1.0 (2026-11-01)

Found by new tests (Monte Carlo over 2400 random mechanisms, gamepad-driven Driver Hub run, Android 7.1 checks):

- SysId: a single glitched velocity reading could set the motion threshold so high that the slow tests were dropped,
  and the fit then "explained" the data with kS alone (R² 1.0, wrong). The top speed is now robust (98th percentile),
  and a test that contributes no data makes the result POOR.
- SysId: every constant now has an uncertainty (`+/- kS 3% kV 1% ...`). GOOD requires it to be small, and OK names
  each constant that is not sure. Mechanisms faster than the hub's velocity averaging get "kA approximate".
- SysId: a missing or too-short test (e.g. a dynamic test that hits the limit in a few samples) now gives POOR and
  says what to change, instead of a confident result.
- SysId: backlash. In the slow ramps only the last continuous motion is used, so the unloaded crossing of the
  gearbox play does not pull kS down.
- Driver Hub: the left stick moves the mechanism between tests (capped, stops at the soft limits). A lift that slid
  down after a test could not be repositioned before. The "at limit" message now says which way to move it.
- CI: the library is checked against the Android 7.1 API (Control Hub) and converted with D8. The SysId Monte Carlo
  runs on every push.

## 1.0.0 (2026-11-01)

First public release of ASA Black Box and ASA SysId.

- SysId: 4 automatic tests, integral-form two-stage fit (instrumental variable for kA), REV hub velocity-lag
  measurement and compensation, encoder-glitch and standstill-offset rejection, clear diagnostics instead of
  bad constants, Java constants + `Feedforward`, PID vs feedforward verification (RB on the Driver Hub).
- Black Box: whole hardware map in one line, events, stall / encoder-lost / brownout / slow-loop rules,
  session baseline, slow reads spread over loops, disk errors shown on the Driver Hub.
- Shared: web UI on the Control Hub (port 8099), FTC Dashboard live graphs when Dashboard is installed,
  AdvantageScope export (.wpilog, validated with the official WPILib reader).
- Verified: compiles against official FTC SDK 12.0.0 and 9.2.0; self-tests in CI; end to end in the ASA
  ftc-sim physics simulator. Not yet on a physical Control Hub: please send results (issue template).
