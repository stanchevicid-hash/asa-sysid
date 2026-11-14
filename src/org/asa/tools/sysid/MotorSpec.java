package org.asa.tools.sysid;

/**
 * Datasheet constants of the bare motor (before the gearbox), 12 V. Used to turn a drivetrain's kV and kA into the
 * robot's mass. The gear ratio and the wheel size are not needed: they cancel out.
 */
public final class MotorSpec {

    /** goBILDA 5203 Yellow Jacket (every ratio uses the same motor): 1.47 kg*cm, 9.2 A stall, 6000 rpm. */
    public static final MotorSpec GOBILDA_5203 = new MotorSpec(1.47 * 0.0980665, 9.2, 6000, 7e-6, 0.93);
    /** REV HD Hex: 0.105 N*m, 8.5 A stall, 6000 rpm. */
    public static final MotorSpec REV_HD_HEX = new MotorSpec(0.105, 8.5, 6000, 6e-6, 0.90);

    public final double stallTorque, stallAmps, freeRpm, rotorInertia, gearboxEfficiency;

    /**
     * @param stallTorque       N*m at the motor shaft
     * @param rotorInertia      kg*m^2 of the motor's rotor (it spins fast through the gearbox, so it adds apparent mass)
     * @param gearboxEfficiency 0..1, torque that reaches the wheel
     */
    public MotorSpec(double stallTorque, double stallAmps, double freeRpm, double rotorInertia, double gearboxEfficiency) {
        this.stallTorque = stallTorque; this.stallAmps = stallAmps; this.freeRpm = freeRpm;
        this.rotorInertia = rotorInertia; this.gearboxEfficiency = gearboxEfficiency;
    }

    /** Winding resistance, ohms. */
    public double resistance() { return 12.0 / stallAmps; }
    /** Torque per amp at the motor shaft, N*m/A. */
    public double kt() { return stallTorque / stallAmps; }
    /** Volts per rad/s of the motor shaft (back-EMF). */
    public double ke() { return 12.0 / (freeRpm * 2 * Math.PI / 60); }

    /**
     * Robot mass in kg from a drivetrain fit in meters (SysId SIMPLE: kV in V/(m/s), kA in V/(m/s^2)).
     * Physics: kV = ke*G/r and kA = m_eff*R*r / (N*kt*eff*G), so m_eff = N*kA*kV*kt*eff / (R*ke); the rotors spinning
     * G/r times faster than the wheels move add N*J*(G/r)^2 of apparent mass, which is taken out.
     */
    public double robotMassKg(int motors, double kV, double kA) {
        double effective = motors * kA * kV * kt() * gearboxEfficiency / (resistance() * ke());
        double gOverR = kV / ke();
        return effective - motors * rotorInertia * gOverR * gOverR;
    }
}
