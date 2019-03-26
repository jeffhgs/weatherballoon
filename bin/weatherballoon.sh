#!/bin/bash
adirBase="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -cp "$adirBase/bcprov-jdk15on-1.56.jar:$adirBase/weatherballoon_shadow.jar" com.groovescale.weatherballoon.Main "$@"

