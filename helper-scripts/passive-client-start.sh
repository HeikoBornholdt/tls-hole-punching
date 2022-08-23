#!/bin/bash
cd /root/tls-hole-punching
git pull
tmux new-session -d -s 'sequential-full-passive-client' 'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="passive-client --server-host 167.235.131.30 --server-port 8000 --bind-port 8000 --target-port 8100"'
tmux new-session -d -s 'parallel-full-passive-client'   'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="passive-client --server-host 167.235.131.30 --server-port 9000 --bind-port 9000 --target-port 9100"'
tmux new-session -d -s 'sequential-0rtt-passive-client' 'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="passive-client --server-host 167.235.131.30 --server-port 10000 --bind-port 10000 --target-port 10100"'
tmux new-session -d -s 'parallel-0rtt-passive-client'   'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="passive-client --server-host 167.235.131.30 --server-port 11000 --bind-port 11000 --target-port 11100"'

cd /root/quiche
git checkout 0.14.0
tmux new-session -d -s 'sequential-full-quic' 'cargo run --bin quiche-server -- --cert apps/src/bin/cert.crt --key apps/src/bin/cert.key --listen 127.0.0.1:8100  --no-retry'
tmux new-session -d -s 'parallel-full-quic'   'cargo run --bin quiche-server -- --cert apps/src/bin/cert.crt --key apps/src/bin/cert.key --listen 127.0.0.1:9100  --no-retry'
tmux new-session -d -s 'sequential-0rtt-quic' 'cargo run --bin quiche-server -- --cert apps/src/bin/cert.crt --key apps/src/bin/cert.key --listen 127.0.0.1:10100 --no-retry --early-data'
tmux new-session -d -s 'parallel-0rtt-quic'   'cargo run --bin quiche-server -- --cert apps/src/bin/cert.crt --key apps/src/bin/cert.key --listen 127.0.0.1:11100 --no-retry --early-data'
