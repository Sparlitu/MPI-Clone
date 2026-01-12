import java.io.*;
import java.net.*;
import java.util.*;

public class smpd {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java smpd <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("smpd listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleRequest(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleRequest(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                // We use raw OutputStream to send bytes from process output directly
                OutputStream out = socket.getOutputStream();) {
            // Protocol:
            // Line 1: Executable
            // Line 2: Arguments (space separated)
            // Line 3: Env Vars (KEY=VAL;KEY=VAL)
            // Line 4: Working Directory

            String executable = in.readLine();
            String argsLine = in.readLine();
            String envLine = in.readLine();
            String dirLine = in.readLine();

            System.out.println("Received launch request: " + executable + " " + argsLine);

            List<String> command = new ArrayList<>();
            command.add(executable);
            if (argsLine != null && !argsLine.isEmpty()) {
                command.addAll(Arrays.asList(argsLine.split(" ")));
            }

            ProcessBuilder pb = new ProcessBuilder(command);

            // Set Env
            if (envLine != null && !envLine.isEmpty()) {
                Map<String, String> env = pb.environment();
                for (String entry : envLine.split(";")) {
                    String[] parts = entry.split("=", 2);
                    if (parts.length == 2) {
                        env.put(parts[0], parts[1]);
                    }
                }
            }

            // Set Working Directory
            if (dirLine != null && !dirLine.isEmpty()) {
                pb.directory(new File(dirLine));
            } else {
                // Default to current directory if not specified
                pb.directory(new File("."));
            }

            // Redirect Error to Output so we only handle one stream
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Pipe process stdout to socket
            InputStream processOut = process.getInputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = processOut.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }

            process.waitFor();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
