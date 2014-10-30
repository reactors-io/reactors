#!/bin/sh

set -x

SCRIPT_PATH=`dirname $0`

. $SCRIPT_PATH/../version.conf

PROJECT_NAME="reactive-collections"
VERSION="$reactive_collections_major.$reactive_collections_minor"
TMP_DOCS_DIR=`mktemp -d`
WORKING_DIR=`mktemp -d`
REPO_GIT_URL="git@github.com:storm-enroute/apidocs.git"
SCALA_VERSION="2.10"
DOCS_SUBDIR_NAME="$PROJECT_NAME/$VERSION/"
DOCS_SOURCE_PATH="$SCRIPT_PATH/../target/scala-$SCALA_VERSION/api"

cp -R $DOCS_SOURCE_PATH $TMP_DOCS_DIR

cd $WORKING_DIR
pwd

if [ -d .git ] || git rev-parse --git-dir > /dev/null 2>&1; then
        echo "Already git repo."
else
        git clone $REPO_GIT_URL .
fi

git fetch --all
git checkout gh-pages

rm -rf $DOCS_SUBDIR_NAME
mkdir $DOCS_SUBDIR_NAME
mv $TMP_DOCS_DIR/* $DOCS_SUBDIR_NAME
rm -rf $TMP_DOCS_DIR
echo "Moved docs dir."
ls -a
git status

echo "Adding $DOCS_SUBDIR_NAME."
git add $DOCS_SUBDIR_NAME
git commit --amend -m "Updating $DIR_NAME."
echo "Committed $DOCS_SUBDIR_NAME."
ls -a
git push --force

cd ..
rm -rf $WORKING_DIR
