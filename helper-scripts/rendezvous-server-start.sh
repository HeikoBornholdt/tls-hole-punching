#!/bin/bash
cd /root/tls-hole-punching
git pull
tmux new-session -d -s 'sequential-full' 'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="server --bind-host=167.235.131.30 --bind-port=8000"'
tmux new-session -d -s 'parallel-full'   'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="server --bind-host=167.235.131.30 --bind-port=9000"'
tmux new-session -d -s 'sequential-0rtt' 'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="server --bind-host=167.235.131.30 --bind-port=10000"'
tmux new-session -d -s 'parallel-0rtt'   'mvn compile exec:java -Dexec.mainClass="de.uhh.Main" -Dexec.args="server --bind-host=167.235.131.30 --bind-port=11000"'
