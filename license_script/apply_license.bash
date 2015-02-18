#!/bin/bash
for f in $(find . -name '*.java' -type f); do
 cp $f $f.bak
 cat "license" $f.bak > $f
 done