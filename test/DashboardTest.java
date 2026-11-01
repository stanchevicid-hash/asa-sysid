import com.acmerobotics.dashboard.FtcDashboard;

import org.asa.tools.core.Dashboard;

import java.util.Arrays;

/**
 * Run with test/fakes on the classpath (an FTC Dashboard look-alike):
 *   javac -d out src/org/asa/tools/core/*.java test/fakes/com/acmerobotics/dashboard/*.java test/fakes/com/acmerobotics/dashboard/telemetry/*.java test/DashboardTest.java
 *   java -cp out DashboardTest
 * Without the fakes, Dashboard.available() must be false (covered by the self-tests).
 */
public class DashboardTest {
    public static void main(String[] args) throws Exception {
        int fail = 0;
        if (!Dashboard.available()) { System.out.println("FAIL dashboard not found"); fail++; }
        Dashboard.send(Arrays.asList("lift.amps", "lift.vel", "broken"), new double[]{2.5, 100, Double.NaN}, Arrays.asList("WARNING lift-stall"), 0);
        if (FtcDashboard.packets != 1) { System.out.println("FAIL packets " + FtcDashboard.packets); fail++; }
        Object amps = FtcDashboard.last.data.get("lift.amps");
        if (!(amps instanceof Double) || (Double) amps != 2.5) { System.out.println("FAIL value " + amps); fail++; }
        if (FtcDashboard.last.data.containsKey("broken")) { System.out.println("FAIL NaN must be skipped"); fail++; }
        if (!"WARNING lift-stall".equals(FtcDashboard.last.data.get("msg0"))) { System.out.println("FAIL message"); fail++; }
        Dashboard.send(Arrays.asList("x"), new double[]{1}, null, 1000);   // rate limit: within 1 s of the last one
        if (FtcDashboard.packets != 1) { System.out.println("FAIL rate limit, packets " + FtcDashboard.packets); fail++; }
        System.out.println(fail == 0 ? "DASHBOARD ok: " + FtcDashboard.last.data : fail + " FAILED");
        System.exit(fail == 0 ? 0 : 1);
    }
}
