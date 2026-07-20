// Chapter 1 - Introduction
// Self-contained Chisel project for the tutorial.
//
// The dependency versions below are pinned to exactly match the main
// chisel-book build (Chisel 6.5.0 / Scala 2.13.14 / chiseltest 6.0.0).
// Because sbt shares one global Coursier/Ivy download cache across every
// project on your machine, these artifacts are downloaded only once and
// then reused here -- opening this project does NOT re-download Chisel.

scalaVersion := "2.13.14"

val chiselVersion = "6.5.0"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:reflectiveCalls",
)

addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "6.0.0"
