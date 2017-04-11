
name := """dslink-scala-jira"""

organization := "com.aviata"

version := "1.0.0"

scalaVersion := "2.11.8"

resolvers += Resolver.jcenterRepo

enablePlugins(JavaAppPackaging)

run in Compile <<= Defaults.runTask(fullClasspath in Compile, mainClass in (Compile, run), runner in (Compile, run))

mappings in Universal += file("dslink.json") -> "dslink.json"

libraryDependencies ++= Seq(
  "joda-time" % "joda-time" % "2.9.7",
  "com.typesafe.play" %% "play-json" % "2.5.13",
  "org.iot-dsa" % "dslink" % "0.16.0",
  "org.scaldi" %% "scaldi" % "0.5.8",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)