#!/bin/bash
tmux kill-session -t 'sequential-full'
tmux kill-session -t 'parallel-full'
tmux kill-session -t 'sequential-0rtt'
tmux kill-session -t 'parallel-0rtt'
