#!/bin/bash

adirLog="$1"
shift

"$@" 2>&1 | tee -a "$adirLog"
