#!/bin/sh

set -e
set -x

VERSION=1.0.0
DSLINK_NAME=dslink-scala-jira
ARTIFACT=$DSLINK_NAME-$VERSION
DEPLOY_DIR=deploy
ZIP_FILE=$ARTIFACT.zip
ZIP_SRC=target/universal/$ZIP_FILE
ZIP_DST=$DEPLOY_DIR/$ZIP_FILE
DSA_DIR=$HOME/dsa
DSLINKS_DIR=$DSA_DIR/dglux-server/dslinks
DSLINK_SRC=$DEPLOY_DIR/$ARTIFACT
DSLINK_DST=$DSLINKS_DIR/$DSLINK_NAME

# Create the new package
./activator dist

rm -rf $DEPLOY_DIR || true
mkdir $DEPLOY_DIR
cp $ZIP_SRC $ZIP_DST

unzip $ZIP_DST -d $DEPLOY_DIR
rm -rf $DSLINK_DST || true
mv $DSLINK_SRC $DSLINK_DST

