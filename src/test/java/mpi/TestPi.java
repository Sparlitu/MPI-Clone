package mpi;

public class TestPi {
    public static void main(String[] args) {
        MPI.init();
        int rank = MPI.comm_rank();
        int size = MPI.comm_size();

        // 1. Sync all processes before starting
        MPI.barrier();

        long iterations = 10_000_000;
        System.out.println("Rank " + rank + " started computation (" + iterations + " iterations)...");

        long start = System.currentTimeMillis();

        // 2. Compute Pi locally (Monte Carlo)
        int hits = 0;
        for (long i = 0; i < iterations; i++) {
            double x = Math.random();
            double y = Math.random();
            if (x * x + y * y <= 1.0) {
                hits++;
            }
        }

        long end = System.currentTimeMillis();
        System.out.println("Rank " + rank + " finished in " + (end - start) + "ms. Hits: " + hits);

        // 3. Aggregate results (Manual Reduce)
        if (rank == 0) {
            long totalHits = hits;
            long processed = 1; // self

            // Receive from all other ranks
            for (int i = 1; i < size; i++) {
                String msg = MPI.receive();
                try {
                    long remoteHits = Long.parseLong(msg);
                    totalHits += remoteHits;
                    processed++;
                } catch (Exception e) {
                    System.err.println("Rank 0: Error parsing message '" + msg + "'");
                }
            }

            double pi = 4.0 * totalHits / (iterations * size);
            System.out.println("-----------------------------------------");
            System.out.println("Total Hits: " + totalHits + " / " + (iterations * size));
            System.out.println("Estimated Pi: " + pi);
            System.out.println("Java Math.PI: " + Math.PI);
            System.out.println("-----------------------------------------");

        } else {
            // Send local hits to Rank 0
            MPI.send(0, String.valueOf(hits));
        }

        MPI.finalize_mpi();
    }
}
