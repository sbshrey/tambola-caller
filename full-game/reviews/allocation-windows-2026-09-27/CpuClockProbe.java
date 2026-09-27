import java.lang.management.ManagementFactory;
public class CpuClockProbe {
    static volatile long sink;
    public static void main(String[] args) throws Exception {
        var clock = ManagementFactory.getThreadMXBean();
        if (!clock.isCurrentThreadCpuTimeSupported()) throw new AssertionError("Unsupported CPU clock");
        clock.setThreadCpuTimeEnabled(true);
        for (int sample = 0; sample < 20; sample++) {
            long wall = System.nanoTime(), cpu = clock.getCurrentThreadCpuTime();
            while (System.nanoTime() - wall < 5_000_000) sink++;
            long used = clock.getCurrentThreadCpuTime() - cpu, elapsed = System.nanoTime() - wall;
            System.out.println("CPU_CLOCK|" + elapsed + "|" + used);
            Thread.sleep(5);
        }
    }
}
