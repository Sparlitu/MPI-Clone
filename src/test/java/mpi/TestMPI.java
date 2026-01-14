package mpi;

public class TestMPI {
    public static void main(String[] args) {
        MPI.init();

        int rank = MPI.comm_rank();
        int size = MPI.comm_size();

        System.out.println("Process " + rank + " of " + size + " started.");

        if (size < 2) {
            System.out.println("Need at least 2 processes for this test.");
            MPI.finalize_mpi();
            return;
        }

        // --- Test 1: Point to Point ---
        if (rank == 0) {
            String msg = "Hello from Master";
            System.out.println("Rank 0 sending: " + msg);
            MPI.send(1, msg);
        } else if (rank == 1) {
            System.out.println("Rank 1 waiting for message...");
            String msg = MPI.receive();
            System.out.println("Rank 1 received: " + msg);
        }

        // --- Test 2: Barrier ---
        // Sleep random amount to desynchronize
        try {
            Thread.sleep((long) (Math.random() * 2000));
        } catch (Exception e) {
        }

        System.out.println("Rank " + rank + " entering barrier...");
        MPI.barrier();
        System.out.println("Rank " + rank + " passed barrier.");

        // --- Test 3: Broadcast ---
        if (rank == 0) {
            System.out.println("Rank 0 Broadcasting...");
            MPI.bcast(0, "Global Message from 0");
        } else {
            System.out.println("Rank " + rank + " Waiting for Bcast...");
            String b = MPI.bcast(0, "");
            System.out.println("Rank " + rank + " Received Bcast: " + b);
        }

        // Sleep a bit to ensure output is flushed
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        MPI.finalize_mpi();
    }
}
