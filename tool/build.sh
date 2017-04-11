#!/bin/sh
./activator clean compile dist
cp target/universal/dslink-scala-jira-1.0.0.zip ../../files/dslink-scala-jira.zip
