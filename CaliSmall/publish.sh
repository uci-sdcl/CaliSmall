#!/bin/bash
# Pushes the new Javadoc to the gh-pages branch.
# To be called AFTER the Javadoc has been generated.
# by Michele Bonazza
GH_PAGES_FOLDER=/Users/michelebonazza/prove/CaliSmall

cd $GH_PAGES_FOLDER

git add . || exit $?
git commit -a -m "generated javadoc"
git push origin gh-pages
