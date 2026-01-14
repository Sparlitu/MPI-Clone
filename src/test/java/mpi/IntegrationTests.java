package mpi;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assertions;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class IntegrationTests {

    private static List<Process> smpdProcesses = new ArrayList<>();
    private static final int[] PORTS = { 5001, 5002, 5003, 5004, 5005, 5006 };

    @BeforeAll
    public static void setup() throws IOException, InterruptedException {
        // Build the classpath string
        String classpath = System.getProperty("java.class.path");
        String javaBin = ProcessHandle.current().info().command().orElse("java");

        // Start SMPD instances
        for (int port : PORTS) {
            System.out.println("Starting SMPD on port " + port);
            ProcessBuilder pb = new ProcessBuilder(
                    javaBin, "-cp", classpath, "mpi.smpd", String.valueOf(port));
            pb.inheritIO();
            smpdProcesses.add(pb.start());
        }
        // Give them time to start
        Thread.sleep(1000);
    }

    @AfterAll
    public static void teardown() {
        for (Process p : smpdProcesses) {
            p.destroyForcibly();
        }
    }

    @Test
    public void testPi() throws Exception {
        System.out.println("\n=== Running TestPi ===");
        String output = runMpiExec("mpi.TestPi", 4);
        System.out.println(output);
        Assertions.assertTrue(output.contains("Estimated Pi:"), "Output should contain 'Estimated Pi'");
        Assertions.assertTrue(output.contains("Total Hits:"), "Output should contain 'Total Hits'");
    }

    @Test
    public void testBroadcast() throws Exception {
        System.out.println("\n=== Running TestBroadcast ===");
        String output = runMpiExec("mpi.TestMPI", 6);
        System.out.println(output);
        // mpi.TestMPI prints "Received Bcast: Global Message from 0"
        Assertions.assertTrue(output.contains("Received Bcast: Global Message from 0"),
                "Broadcast message not found in output");
        // We expect ranks 1..5 to receive it (assuming rank 0 sends)
        int successCount = output.split("Received Bcast: Global Message from 0").length - 1;
        Assertions.assertTrue(successCount >= 5, "Expected at least 5 receivers for broadcast, found " + successCount);
    }

    @Test
    public void testRace() throws Exception {
        System.out.println("\n=== Running TestRace ===");
        String output = runMpiExec("mpi.TestRace", 2);
        System.out.println(output);
        Assertions.assertTrue(output.contains("TEST PASSED: P2P Message received"), "Race condition test failed!");
        Assertions.assertFalse(output.contains("TEST FAILED"), "Test explicitly reported failure");
    }

    private String runMpiExec(String testClass, int numProcs) throws Exception {
        String classpath = System.getProperty("java.class.path");
        String javaBin = ProcessHandle.current().info().command().orElse("java");

        // Construct mpiexec arguments
        // mpiexec -processes N port1 1 port2 1 ... java -cp CP TestClass
        List<String> args = new ArrayList<>();
        args.add(javaBin);
        args.add("-cp");
        args.add(classpath);
        args.add("mpi.mpiexec");
        args.add("-processes");
        args.add(String.valueOf(numProcs));

        for (int i = 0; i < numProcs; i++) {
            args.add(String.valueOf(PORTS[i]));
            args.add("1");
        }

        // The program to run
        args.add(javaBin);
        args.add("-cp");
        args.add(classpath);
        args.add(testClass);

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        // Capture output
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        int exitCode = p.waitFor();
        if (exitCode != 0) {
            Assertions.fail("mpiexec failed with exit code " + exitCode + "\nOutput:\n" + sb.toString());
        }
        return sb.toString();
    }
}
