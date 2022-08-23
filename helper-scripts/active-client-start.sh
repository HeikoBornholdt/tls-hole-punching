#!/bin/bash
cd /root/tls-hole-punching
git pull
rm *.csv

tmux new-session -s 'sequential-full-active-client' -d
tmux send-keys -t 'sequential-full-active-client' 'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="active-client --server-host 167.235.131.30 --server-port 8000  --bind-port 8000  --sequential --logfile sequential-full.csv"' Enter
tmux new-session -s 'parallel-full-active-client' -d
tmux send-keys -t 'parallel-full-active-client' 'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="active-client --server-host 167.235.131.30 --server-port 9000  --bind-port 9000  --parallel   --logfile parallel-full.csv"' Enter
tmux new-session -s 'sequential-0rtt-active-client' -d
tmux send-keys -t 'sequential-0rtt-active-client' 'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="active-client --server-host 167.235.131.30 --server-port 10000 --bind-port 10000 --sequential --logfile sequential-0rtt.csv"' Enter
tmux new-session -s 'parallel-0rtt-active-client' -d
tmux send-keys -t 'parallel-0rtt-active-client' 'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="active-client --server-host 167.235.131.30 --server-port 11000 --bind-port 11000 --parallel   --logfile parallel-0rtt.csv"' Enter
#tmux new-session -d -s 'sequential-full-active-client' 'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="active-client --server-host 167.235.131.30 --server-port 8000  --bind-port 8000  --sequential --logfile sequential-full.csv"'
#tmux new-session -d -s 'parallel-full-active-client'   'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="active-client --server-host 167.235.131.30 --server-port 9000  --bind-port 9000  --parallel   --logfile parallel-full.csv"'
#tmux new-session -d -s 'sequential-0rtt-active-client' 'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="active-client --server-host 167.235.131.30 --server-port 10000 --bind-port 10000 --sequential --logfile sequential-0rtt.csv"'
#tmux new-session -d -s 'parallel-0rtt-active-client'   'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="active-client --server-host 167.235.131.30 --server-port 11000 --bind-port 11000 --parallel   --logfile parallel-0rtt.csv"'

echo "Wait 30s to make sure active-clients are compiled and ready..."
sleep 30 

cd /root/quiche
git checkout 0.14.0
rm sequential-0rtt-quic.session parallel-0rtt-quic.session
tmux new-session -d -s 'sequential-full-quic' 'sleep 0; watch -n20 cargo run --bin quiche-client -- https://127.0.0.1:8000  --no-verify --wire-version 1'
tmux new-session -d -s 'parallel-full-quic'   'sleep 3; watch -n20 cargo run --bin quiche-client -- https://127.0.0.1:9000  --no-verify --wire-version 1'
tmux new-session -d -s 'sequential-0rtt-quic' 'sleep 5; watch -n20 cargo run --bin quiche-client -- https://127.0.0.1:10000 --no-verify --wire-version 1 --early-data --session-file sequential-0rtt-quic.session'
tmux new-session -d -s 'parallel-0rtt-quic'   'sleep 8; watch -n20 cargo run --bin quiche-client -- https://127.0.0.1:11000 --no-verify --wire-version 1 --early-data --session-file parallel-0rtt-quic.session'
