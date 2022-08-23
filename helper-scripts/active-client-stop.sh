#!/bin/bash
tmux kill-session -t 'sequential-full-active-client'
tmux kill-session -t 'parallel-full-active-client'
tmux kill-session -t 'sequential-0rtt-active-client'
tmux kill-session -t 'parallel-0rtt-active-client'

tmux kill-session -t 'sequential-full-quic'
tmux kill-session -t 'parallel-full-quic'
tmux kill-session -t 'sequential-0rtt-quic'
tmux kill-session -t 'parallel-0rtt-quic'
