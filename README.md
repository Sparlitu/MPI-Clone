# MPI Clone in Java

A robust, simplified implementation of the MPI (Message Passing Interface) standard in Java. This project provides a complete infrastructure for distributed computing, enabling the development and execution of parallel algorithms using standard MPI primitives.

## Features

### Core Components
- **`mpiexec`**: The client application and message router. It launches processes on remote nodes and routes messages between them.
- **`smpd` (Simple Message Process Daemon)**: A lightweight daemon running on each compute node (or port) that spawns and manages child processes.
- **`MessageRouter`**: A centralized routing logic within `mpiexec` that handles the plumbing of messages between ranks.

### MPI Primitives
- **Point-to-Point**: `MPI.send(dest, msg)` and `MPI.receive()` for direct process communication.
- **Collectives**: 
  - `MPI.bcast(root, data)`: Efficiently broadcasts data from a root rank to all other ranks.
  - `MPI.barrier()`: Synchronization point for all processes.
- **Process Management**: `MPI.init()`, `MPI.finalize_mpi()`, `MPI.comm_rank()`, `MPI.comm_size()`.

### Reliability
- **Race Condition Handling**: sophisticated message buffering ensures that messages arriving out of order (e.g., during a collective operation) are queued and delivered correctly.
- **Error Propagation**: Detects process failures and propagates errors to the process manager.

## Architecture

The system follows a star topology for control and routing during the prototype phase:

1.  **Start SMPJs**: `smpd` instances are started on available ports (e.g., 5001-5006).
2.  **Launch**: `mpiexec` connects to these `smpd` ports.
3.  **Spawn**: `mpiexec` instructs `smpd` to spawn the user's Java class (e.g., `TestPi`).
4.  **Connect**: The spawned processes connect back to `mpiexec` (the router).
5.  **Communicate**: 
    - Processes send messages to the Router.
    - Router forwards them to the destination Process.

## Prerequisites

- **Java JDK 21** or higher.
- **Gradle** (wrapper included).

## Build & Test

The project uses Gradle for building and testing.

### Running Automated Tests

The integration tests spin up a full local cluster (using multiple processes) and run various scenarios (Pi calculation, Race conditions, Broadcast).

```bash
./gradlew clean test
```

### Building the Project

To compile and create distributable scripts:

```bash
./gradlew installDist
```
The executables will be available in `build/install/MPI-Clone/bin/`.

## Manual Usage

You can run MPI programs manually by mimicking the behavior of the integration tests.

**Step 1: Start SMPD Daemons**
Start a few instances of the process daemon on different ports.
```bash
# Terminal 1
java -cp "build/classes/java/main" mpi.smpd 5001

# Terminal 2
java -cp "build/classes/java/main" mpi.smpd 5002
```

**Step 2: Run mpiexec**
Launch your application (`mpi.TestPi` or your own class) using `mpiexec`.
```bash
# Terminal 3
java -cp "build/classes/java/main:build/classes/java/test" mpi.mpiexec \
    -processes 2 \
    5001 1 \
    5002 1 \
    java -cp "build/classes/java/main:build/classes/java/test" mpi.TestPi
```

### Syntax for mpiexec
```bash
mpiexec -processes <N> <host1> <num_slots> ... <hostN> <num_slots> <command_to_run>
```
- `-processes <N>`: Total number of processes to start.
- `<host> <num_slots>`: The host (or port in this local version) and how many processes to launch on it.
- `<command_to_run>`: The full command line for the MPI program.

## Troubleshooting

- **Address In Use**: If `smpd` fails to start, check if the port is already taken (`lsof -i :5001`).
- **ClassNotFound**: Ensure your classpath includes both `main` and `test` output directories if running test classes manually.
- **Timeout/Hanging**: If `MPI.receive()` hangs, ensure the sender is actually sending data to the correct rank. `MPI.barrier()` requires **all** started processes to reach it.
