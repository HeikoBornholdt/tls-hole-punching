#!/bin/bash
tmux kill-session -t 'sequential-full-passive-client'
tmux kill-session -t 'parallel-full-passive-client'
tmux kill-session -t 'sequential-0rtt-passive-client'
tmux kill-session -t 'parallel-0rtt-passive-client'

tmux kill-session -t 'sequential-full-quic'
tmux kill-session -t 'parallel-full-quic'
tmux kill-session -t 'sequential-0rtt-quic'
tmux kill-session -t 'parallel-0rtt-quic'
