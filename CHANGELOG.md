# Changelog

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
