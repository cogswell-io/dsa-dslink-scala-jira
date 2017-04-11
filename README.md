# dsa-dslink-scala-jira
DSLink for interfacing with JIRA

You need to download DGLux and extract it into your home directory in order
for the deploy script to work seemlessly.

Updated dependencies for Scala IDE:
```
$ ./activator eclipse
```

Run all unit tests:
```
$ ./activator test
```

Build the zip file containing the DSLink:
```
$ ./activator dist
```

Deploy the DSLink into the DSA extracted to you home directory:
```
$ ./deploy.sh
```

