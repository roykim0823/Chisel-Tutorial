// Chapter 13 - Debugging, Testing, and Verification
// Self-contained Chisel project for the tutorial.
// Same pinned versions as the other chapters and the main chisel-book build,
// so the shared Coursier/Ivy cache is reused and nothing is re-downloaded.

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

// Formal verification (§13.4) needs an SMT solver on the PATH. Those tests are
// tagged NeedsSolver and excluded here so that a plain `sbt test` works with no
// native tools at all. Run them with:  sbt "testOnly * -- -n NeedsSolver"
Test / test / testOptions += Tests.Argument("-l", "NeedsSolver")
