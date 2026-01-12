#!/bin/bash

# Kill any existing smpd or java processes (be careful in real env, but okay here)
pkill -f smpd

echo "Starting SMPD on port 5001..."
java -cp bin smpd 5001 > smpd_5001.log 2>&1 &
SMPD1_PID=$!

echo "Starting SMPD on port 5002..."
java -cp bin smpd 5002 > smpd_5002.log 2>&1 &
SMPD2_PID=$!

sleep 1

echo "Running mpiexec..."
# Launching 1 process on 5001 and 1 process on 5002
# Total 2 processes.
# Command: java -cp bin TestMPI
java -cp bin mpiexec -processes 2 5001 1 5002 1 java -cp bin TestMPI

echo "Cleaning up..."
kill $SMPD1_PID
kill $SMPD2_PID
