#!/bin/bash
adirBase="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"

java -cp "$adirBase/reports/libs/bcprov-jdk15on-1.56.jar:$adirBase/build/libs/weatherballoon_shadow.jar" com.groovescale.weatherballoon.Run1 "$@"
