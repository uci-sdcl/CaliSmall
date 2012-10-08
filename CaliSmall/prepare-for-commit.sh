#!/bin/bash
# Prepares the gh-pages local repository for being updated with the new javadoc file.
# by Michele Bonazza

GH_PAGES_FOLDER=/Users/michelebonazza/prove/CaliSmall

cd $GH_PAGES_FOLDER

git pull origin gh-pages || exit $?

# Clear out the old files
ls * | xargs rm -rf
