# Changelog

## 1.5.0 (2026-11-21)

Not yet run on a real robot: the results of our robot test come in 1.5.1. Anything odd? Switch the automatic
recording off with Y in "ASA Black Box" and tell us.

- **Code review (12 parallel reviewers) and the fixes it led to:**
  - automatic recording: motors on an Expansion Hub were matched to the Control Hub (same USB serial): now by module;
    position / velocity sign now as getCurrentPosition() gives it (CCW motor types: goBILDA, REV HD Hex...); hubs the
    team caches (AUTO / MANUAL) are read through their cache, bulk reads only for hubs left OFF; a hub that drops out
    no longer reads as "all encoders at 0"; no IMU or I2C sensor at all (HardwareMap.getAll() initializes them under
    its lock, which would hold up the team's INIT); steps aside for any team Black Box (also create + start without
    attachAll) and leaves the Live tab to it;
  - page server: request read with a hard 8 KB limit (a 200 MB line no longer exhausts memory), whole-connection
    deadlines enforced by a reaper (no slot held by a byte every 3 s or by a client that never reads), a download
    started before INIT stops when the robot drives, the Wi-Fi rule applies to any OpMode, recorded or not;
  - health check: B stopping the automatic test no longer turns that motor backward; Y without motors no longer
    crashes; center of gravity: a step must tip the robot at least 3° (two presses on the flat gave a division by 0);
  - Remove ASA: keeps ASA SysId's constants (sysid/); strings and comments can no longer hide the team's use of
    Black Box ("http://" in a string); the renamed public types (AsaRule...) count as use; files with ASA names but
    no mark block removal until the team checks them;
  - recordings of one OpMode in the same second take the next second (the "_2" suffix broke the baseline), the file
    name is claimed atomically;
  - results page: an older slow answer no longer draws over a newer click; SysId graphs and the "worse than PID"
    verdict; Copy Java hidden on verifications; hubs shown by name; friction compared between drive wheels only;
    odometry pods on drive ports not reported as a code fault; keyboard access; leftovers of the first design removed.
- **One program on the Driver Hub: "ASA Black Box".** LB / RB picks the mode (Health check, Center of gravity, Recording test, Remove ASA),
  X the language (English, Russian, Romanian) for everything on the Driver Hub. Replaces "ASA Health Check" and
  "ASA Center of Gravity": uploading the new files over the old ones removes those two from the list.
- **Every match recorded with no code.** Install, restart the robot once, and every OpMode (Java or Blocks) is
  recorded in the background, named after the OpMode and compared with its earlier runs. It never changes the team's
  hubs, reads all encoders of a hub with one bulk read (20 samples/s with one hub, 10 with two), motor current every
  0.4 s, runs below the OpMode's priority, stops itself on any error, steps aside for OpModes that use Black Box in
  their own code, keeps the newest 300 recordings, and drops INIT-then-STOP runs. Y in "ASA Black Box" switches it.
- Without the team's code, "all encoders of a hub went to 0" may be the program resetting them: reported as a
  WARNING with both explanations instead of a CRITICAL hub reset.
- Two recordings of the same OpMode in the same second no longer share a file.
- **Match safety.** Nothing at INIT waits for ASA: the recording attaches, opens its files and reads the hardware on
  its own thread, so a slow disk or hub can never delay the team's init(). While a program drives, the page sends no
  recording over 256 KB and no WPILOG, so the Driver Station keeps the Wi-Fi. In the OnBot bundle, every class with
  an ordinary name gets an "Asa" prefix (AsaRecorder, AsaRule, ...): uploading never replaces a team's own file.
- **Remove ASA with one button** (fourth mode, hold A + B for 3 s): stops the recording and the page at once, deletes
  the folder FIRST/asa and the library's own files in OnBot Java. Only files whose first line is the ASA mark are
  deleted (never a team's own Recorder.java), ASA SysId's shared files stay, and if the team's code still uses Black
  Box nothing is deleted and the files to change are listed. Tested in test/RemoverTest and on a copy of the real 17-file bundle. With the automatic recording switched off (Y), nothing of
  ASA starts at power-on, not even the page. The page server runs below the OpMode's priority.
- **Recording test** (third mode of ASA Black Box): loop time with and without the automatic recording, samples
  recorded, encoders read, as green / yellow / red. The INIT screen shows whether every match is recorded, the
  newest recording and the page address. A 7-step check after installing is in the guides.
- **In the code or in the robot?** Each problem on the results page says where it is. The recording holds what the
  driver did (sticks), what the program commanded (motor power) and what the robot did (encoders, current), so the
  page can tell them apart: sticks pushed but no drive power, power flipping + / − many times a second (an
  over-tuned controller or two pieces of code driving one motor), an encoder counting against its power
  (setDirection or a swapped cable) are **in the code**; no current, a stall, friction, a weak battery are
  **in the robot**. The verdict counts both.
- A motor holding a position at low power (a lift at 0.1) is no longer shown as "not used".
- The results page serves each request on its own thread: a phone downloading a long WPILOG no longer freezes the
  Live tab for everyone (measured: 5 s timeouts before, 1 to 14 ms after). At most 8 requests at once and 5 s to send
  a request, so nobody on the robot Wi-Fi can exhaust the Control Hub with open connections (tested: 50 idle
  connections get "busy" at once, the page answers again 5 s later). Slower polling when the page is hidden.
- Health check: Y after the report runs the automatic test again. Driver Hub hints in the chosen language from the
  first OpMode after power-on. Autonomous detected from the SDK's own list, so Blocks programs count too.
- **The Driver Hub in ASA's colors**: titles in magenta, every hub, motor, battery and tipping limit with a green,
  yellow or red light, the selected option marked, step dots for the center of gravity.
- Automatic recording: the IMU and I2C distance sensors are read only from 1 s after START (the team's INIT may be
  loading the IMU's firmware then), and Autonomous OpModes do not record the gamepads (no false "disconnected").
- OpMode names in any alphabet stay readable ("Маршрут", "Mașină"), and their AdvantageScope files keep the name.
- The page no longer moves away from what someone is reading: a notice offers "Open" instead; it also says when the
  phone has lost the robot Wi-Fi.
- The results page shows the newest 40 recordings first, "Show all" for the rest.
- **The results page starts by itself**: `FtcBlackBox.attachAll` starts it, `AsaWeb.start()` is no longer needed.
  Open it once and it follows the robot: a program starts → Live tab; it stops → its recording opens.
  Opened later, it shows the newest recording. It speaks the language chosen on the Driver Hub.
- **Live graphs**: the last 60 s of battery, motor currents and loop time (any signal can be ticked), about 5 values
  per second. Values with units in the table.
- **Graphs are exact now.** Before, a long session was drawn from every Nth sample, so a one-loop spike could vanish
  (a 9 A stall row in 20000 rows was not drawn). Each pixel now draws the min and max of its samples. Time axis,
  units, min / max, and hover shows the recorded value (min … max when a pixel holds many samples).
- **New look in ASA's colors** (magenta on carbon black, the team logo in the header), light theme when the phone is
  in light mode, a big verdict at the top, recordings as a swipeable strip on phones, graphs with a grid and a soft
  fill, REC on the Live tab while a program runs, a clear "nothing is running" screen. The SysId tab only appears
  when SysId is installed.
- **Center of gravity drawing** on the results page: top view and side view with the point, distances in cm,
  tipping lines with their angles, a traffic light for each tipping limit, SVG download. The phone layout stacks them.

## 1.4.2 (2026-11-16)

- **SysId results in plain words**: use these constants or not (traffic light), what kS / kG / kV / kA mean with their
  precision, every problem explained with what to fix, and the verification as "X% more precise than PID alone".
- The page shows only what is installed: **"ASA Black Box"** or **"ASA SysId"** on its own, **"ASA Tools"** with both.
  Its GitHub and feedback links point to the right repository.

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
