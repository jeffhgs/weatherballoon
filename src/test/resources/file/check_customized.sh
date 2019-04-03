#!/bin/bash
adirScript="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

expected="customized: hello world"
actual=$("$adirScript/customized.sh")
if [[ "$actual" == "$expected" ]]
then
    exit 0
else
    exit 1
fi

