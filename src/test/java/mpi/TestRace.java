package mpi;

public class TestRace {
    public static void main(String[] args) {
        MPI.init();
        int rank = MPI.comm_rank();
        int size = MPI.comm_size();

        if (size < 2) {
            System.out.println("Need 2 ranks");
            MPI.finalize_mpi();
            return;
        }

        // Use a barrier to sync start
        MPI.barrier();

        if (rank == 1) {
            System.out.println("Rank 1: Sending P2P to Rank 0...");
            MPI.send(0, "Important Message");

            System.out.println("Rank 1: Starting Bcast (Root)...");
            MPI.bcast(1, "Broadcast Data");

        } else if (rank == 0) {

            System.out.println("Rank 0: Waiting for Bcast from 1...");
            String b = MPI.bcast(1, "");
            System.out.println("Rank 0: Received Bcast: " + b);

            // Now attempt to receive the P2P message
            System.out.println("Rank 0: Waiting for P2P message...");
            String msg = MPI.receive();
            System.out.println("Rank 0: Received P2P: " + msg);

            if (msg == null) {
                System.out.println("TEST FAILED: P2P Message was lost/null");
            } else if (msg.equals("Important Message")) {
                System.out.println("TEST PASSED: P2P Message received");
            } else {
                System.out.println("TEST FAILED: Wrong message: " + msg);
            }
        }

        MPI.finalize_mpi();
    }
}
