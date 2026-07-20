// Chapter 15 - A RISC-V Pipeline (Wildcat)
// Self-contained Chisel project for the tutorial.
// Same pinned versions as the other chapters and the main chisel-book build,
// so the shared Coursier/Ivy cache is reused and nothing is re-downloaded.
// NOTE: we deliberately do NOT include the `jelf` ELF-loader dependency; this
// project is scoped to the parts of Wildcat that build without external
// program files.

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
