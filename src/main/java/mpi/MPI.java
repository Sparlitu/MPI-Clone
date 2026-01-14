package mpi;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MPI {
    private static int rank;
    private static int size;
    private static String masterHost;
    private static int masterPort;
    private static Socket socket;
    private static DataInputStream in;
    private static DataOutputStream out;

    // Message Buffer for P2P messages received during collective ops
    private static Queue<String> messageQueue = new LinkedList<>();

    public static void init() {
        try {
            String rankStr = System.getenv("MPI_RANK");
            String sizeStr = System.getenv("MPI_SIZE");
            masterHost = System.getenv("MPI_MASTER_HOST");
            String portStr = System.getenv("MPI_MASTER_PORT");

            if (rankStr == null || sizeStr == null || masterHost == null || portStr == null) {
                System.err.println("MPI Environment variables not set. Are you running under mpiexec?");
                System.exit(1);
            }

            rank = Integer.parseInt(rankStr);
            size = Integer.parseInt(sizeStr);
            masterPort = Integer.parseInt(portStr);

            // Connect to Message Router
            socket = new Socket(masterHost, masterPort);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // Handshake (Send Rank)
            out.writeInt(rank);
            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static int comm_size() {
        return size;
    }

    public static int comm_rank() {
        return rank;
    }

    public static void send(int dest, String message) {
        try {
            out.writeInt(1); // CMD=1 (SEND)
            out.writeInt(dest);
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String receive() {
        try {
            // 1. Check Buffer First
            if (!messageQueue.isEmpty()) {
                return messageQueue.poll();
            }

            // 2. Read from Socket
            while (true) {
                int cmd = in.readInt();
                if (cmd == 2) { // CMD=2 (INCOMING MSG)
                    int src = in.readInt(); // consumed but not used in this simple API
                    String msg = in.readUTF();
                    System.out.println("[MPI Debug] Received from " + src);
                    return msg;
                } else {
                    System.err.println("Protocol Error in receive(): Expected MSG (2), got " + cmd);
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void barrier() {
        try {
            out.writeInt(3); // CMD=3 (BARRIER)
            out.flush();

            // Wait for RELEASE
            while (true) {
                int cmd = in.readInt();
                if (cmd == 4) { // CMD=4 (BARRIER_RELEASE)
                    // Consume the rest of the packet sent by sendPacket (int arg1, String arg2)
                    in.readInt();
                    in.readUTF();
                    break;
                } else if (cmd == 2) { // CMD=2 (INCOMING MSG)
                    // Unexpected P2P message, Buffer it!
                    int src = in.readInt();
                    String msg = in.readUTF();
                    System.out.println("[MPI Debug] Buffering P2P msg from " + src + " during Barrier");
                    messageQueue.add(msg);
                } else {
                    System.err.println("Protocol Error: Expected Barrier Release (4), got " + cmd);
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String bcast(int root, String data) {
        try {
            if (rank == root) {
                out.writeInt(5); // CMD=5 (BCAST)
                out.writeUTF(data);
                out.flush();
                return data;
            } else {
                // Non-roots wait for payload
                while (true) {
                    int cmd = in.readInt();
                    if (cmd == 6) { // CMD=6 (BCAST_PAYLOAD)
                        int r = in.readInt(); // Root (from Router)
                        String msg = in.readUTF();
                        return msg;
                    } else if (cmd == 2) { // CMD=2 (INCOMING MSG)
                        // Unexpected P2P message, Buffer it!
                        int src = in.readInt();
                        String msg = in.readUTF();
                        System.out.println("[MPI Debug] Buffering P2P msg from " + src + " during Bcast");
                        messageQueue.add(msg);
                    } else {
                        System.err.println("Protocol Error: Expected Bcast Payload (6), got " + cmd);
                        System.exit(1);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Optional cleanup
    public static void finalize_mpi() {
        try {
            if (socket != null)
                socket.close();
        } catch (IOException e) {
        }
    }
}
