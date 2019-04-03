#!/bin/bash
s=weatherballoon

if ! tmux has-session -t "$s" 2>&-
then
  tmux new-session -d -n "$s" -s "$s"
fi
x="$@"
tmux send-keys -t "$s" "$x"
tmux send-keys -t "$s" Enter
