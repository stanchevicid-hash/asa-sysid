# Changelog

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
