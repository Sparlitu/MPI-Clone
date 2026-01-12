import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class mpiexec {

    // Structure to hold target node info
    static class NodeInfo {
        String host;
        int port;
        int count;

        public NodeInfo(String host, int port, int count) {
            this.host = host;
            this.port = port;
            this.count = count;
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: ");
            System.out.println("  mpiexec -hosts N IP1 N1 ... program");
            System.out.println("  mpiexec -processes N port1 N1 ... program");
            return;
        }

        List<NodeInfo> nodes = new ArrayList<>();
        int argIndex = 0;
        String mode = args[argIndex++]; // -hosts or -processes

        try {
            int numEntries = Integer.parseInt(args[argIndex++]);

            for (int i = 0; i < numEntries; i++) {
                if (mode.equals("-hosts")) {
                    String ip = args[argIndex++];
                    int n = Integer.parseInt(args[argIndex++]);
                    nodes.add(new NodeInfo(ip, 5000, n)); // Default port 5000 for -hosts
                } else if (mode.equals("-processes")) {
                    int p = Integer.parseInt(args[argIndex++]);
                    int n = Integer.parseInt(args[argIndex++]);
                    nodes.add(new NodeInfo("localhost", p, n));
                } else {
                    throw new IllegalArgumentException("Unknown mode: " + mode);
                }
            }

            // Remaining args are program and its args
            List<String> programArgs = new ArrayList<>();
            while (argIndex < args.length) {
                programArgs.add(args[argIndex++]);
            }

            if (programArgs.isEmpty()) {
                System.err.println("Error: No program specified.");
                return;
            }

            String executable = programArgs.get(0);
            String progArgsStr = String.join(" ", programArgs.subList(1, programArgs.size()));
            String workingDir = System.getProperty("user.dir");

            // Calculate totals
            int totalRanks = 0;
            for (NodeInfo node : nodes)
                totalRanks += node.count;

            System.out.println("Launching " + totalRanks + " processes...");

            // Start Message Router
            MessageRouter router = new MessageRouter(totalRanks);
            router.start();
            final int finalTotalRanks = totalRanks;

            String masterHost = InetAddress.getLocalHost().getHostAddress();
            int masterPort = router.getPort();

            // Launch processes
            ExecutorService executor = Executors.newCachedThreadPool();
            List<Future<?>> futures = new ArrayList<>();

            int currentRank = 0;
            for (NodeInfo node : nodes) {
                for (int k = 0; k < node.count; k++) {
                    final int rank = currentRank++;
                    final String host = node.host;
                    final int port = node.port;

                    futures.add(executor.submit(() -> {
                        launchProcess(host, port, rank, finalTotalRanks, masterHost, masterPort,
                                executable, progArgsStr, workingDir);
                        return null;
                    }));
                }
            }

            // Wait for all to finish
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            router.shutdown();
            executor.shutdown();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error parsing arguments or executing.");
        }
    }

    private static void launchProcess(String host, int port, int rank, int size,
            String masterHost, int masterPort,
            String executable, String args, String dir) {
        try (Socket socket = new Socket(host, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Protocol:
            // Line 1: Executable
            // Line 2: Arguments
            // Line 3: Env Vars (MPI_RANK=x;MPI_SIZE=y;...)
            // Line 4: Working Dir

            out.println(executable);
            out.println(args);
            out.println("MPI_RANK=" + rank + ";MPI_SIZE=" + size +
                    ";MPI_MASTER_HOST=" + masterHost + ";MPI_MASTER_PORT=" + masterPort);
            out.println(dir);

            // Read output
            String line;
            while ((line = in.readLine()) != null) {
                synchronized (System.out) {
                    System.out.println("[Rank " + rank + "]: " + line);
                }
            }

        } catch (IOException e) {
            System.err.println("Failed to connect to smpd at " + host + ":" + port);
            e.printStackTrace();
        }
    }

    // --- Message Router for Bonus ---

    static class MessageRouter extends Thread {
        private ServerSocket server;
        private int totalRanks;
        private Map<Integer, Socket> rankSockets = new ConcurrentHashMap<>();
        private Map<Integer, Object> rankLocks = new ConcurrentHashMap<>();

        private int barrierCount = 0;
        private Object barrierLock = new Object();
        private Object bcastLock = new Object();

        public MessageRouter(int totalRanks) throws IOException {
            this.server = new ServerSocket(0); // Random port
            this.totalRanks = totalRanks;
        }

        public int getPort() {
            return server.getLocalPort();
        }

        public void shutdown() {
            try {
                server.close();
            } catch (IOException e) {
            }
        }

        @Override
        public void run() {
            try {
                while (!server.isClosed()) {
                    Socket client = server.accept();
                    new Thread(() -> handleClient(client)).start();
                }
            } catch (IOException e) {
                // Server closed
            }
        }

        private void handleClient(Socket socket) {
            try {
                DataInputStream in = new DataInputStream(socket.getInputStream());

                // Handshake: Read Rank
                int rank = in.readInt();
                rankSockets.put(rank, socket);
                rankLocks.put(rank, new Object());

                System.out.println("Router connected to Rank " + rank);

                // Loop for messages
                while (true) {
                    // Packet format: [CMD, ...]
                    int cmd = in.readInt();
                    if (cmd == 1) { // SEND
                        int dest = in.readInt();
                        String msg = in.readUTF();
                        forwardMessage(dest, rank, msg);
                    } else if (cmd == 3) { // BARRIER
                        handleBarrier();
                    } else if (cmd == 5) { // BCAST
                        String payload = in.readUTF();
                        handleBcast(rank, payload);
                    }
                }
            } catch (EOFException e) {
                // Client disconnected
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void forwardMessage(int dest, int src, String msg) {
            Socket destSocket = rankSockets.get(dest);
            Object lock = rankLocks.get(dest);

            if (destSocket != null && lock != null) {
                synchronized (lock) {
                    try {
                        DataOutputStream out = new DataOutputStream(destSocket.getOutputStream());
                        out.writeInt(2); // CMD=2 (INCOMING MSG)
                        out.writeInt(src); // FROM
                        out.writeUTF(msg); // MSG
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                System.err.println("Router: Unknown destination rank " + dest);
            }
        }

        private void handleBarrier() {
            synchronized (barrierLock) {
                barrierCount++;
                if (barrierCount == totalRanks) {
                    // Release all
                    for (int r : rankSockets.keySet()) {
                        sendPacket(r, 4, 0, ""); // CMD=4 (BARRIER_RELEASE)
                    }
                    barrierCount = 0;
                }
            }
        }

        private void handleBcast(int senderRank, String payload) {
            synchronized (bcastLock) {
                // Flood broadcast to all OTHERS
                for (int r : rankSockets.keySet()) {
                    if (r != senderRank) {
                        sendPacket(r, 6, senderRank, payload); // CMD=6 (BCAST_PAYLOAD)
                    }
                }
            }
        }

        private void sendPacket(int dest, int cmd, int arg1, String arg2) {
            Socket s = rankSockets.get(dest);
            Object lock = rankLocks.get(dest);
            if (s != null && lock != null) {
                synchronized (lock) {
                    try {
                        DataOutputStream out = new DataOutputStream(s.getOutputStream());
                        out.writeInt(cmd);
                        out.writeInt(arg1);
                        out.writeUTF(arg2);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
