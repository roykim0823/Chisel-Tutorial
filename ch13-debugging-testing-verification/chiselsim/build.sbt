// Chapter 13, ChiselSim addendum (§13.2.8).
// A SEPARATE project on purpose: ChiselSim is a Chisel 7 API, and the chapter
// itself is pinned to Chisel 6.5.0 / chiseltest 6.0.0 like the rest of the
// tutorial. Keeping it here lets both sets of tests be real and runnable
// without unpinning the chapter.
scalaVersion := "2.13.18" // Chisel 7 pulls scala-library 2.13.18 (SIP-51)

val chiselVersion = "7.15.0"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:reflectiveCalls",
)

addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % "test"

// §13.4's DUT is SHARED with the chapter project rather than copied, so the
// formal check and the ChiselSim search provably run on the same circuit.
// (Assert.scala and Boring.scala are local copies instead: Boring.scala needs
// the Chisel 7 BoringUtils API, so it genuinely differs.)
Compile / unmanagedSources += baseDirectory.value / ".." / "src" / "main" / "scala" / "Saturate.scala"
