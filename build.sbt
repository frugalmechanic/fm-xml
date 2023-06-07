name := "fm-xml"

description := "XML utilities"

scalaVersion := "3.2.2"

crossScalaVersions := Seq("3.2.2", "2.13.10", "2.12.17", "2.11.12")

val fatalWarnings = Seq(
  // Enable -Xlint, but disable the default 'unused' so we can manually specify below
  "-Xlint:-unused",
  // Remove "params" since we often have method signatures that intentionally have the parameters, but may not be used in every implementation, also omit "patvars" since it isn't part of the default xlint:unused and isn't super helpful
  "-Ywarn-unused:imports,privates,locals",
  // Warnings become Errors
  "-Xfatal-warnings"
)

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-language:implicitConversions",
  "-feature",
  "-Xlint",
) ++ (if (scalaVersion.value.startsWith("2.11")) Seq(
  // Scala 2.11 specific compiler flags
  "-Ywarn-unused-import"
) else Nil) ++ (if (scalaVersion.value.startsWith("2.12") || scalaVersion.value.startsWith("2.13")) Seq(
  // Scala 2.12/2.13 specific compiler flags
  "-opt:l:inline",
  "-opt-inline-from:<sources>"
) ++ fatalWarnings else Nil) ++ (if (scalaVersion.value.startsWith("3")) Seq(
  //"-Yno-decode-stacktraces"
) else Nil)

javacOptions ++= Seq("-source", "11", "-target", "11")

libraryDependencies ++= Seq(
  "com.frugalmechanic" %% "fm-common" % "1.0.0",
  "com.frugalmechanic" %% "fm-lazyseq" % "1.0.0",
  "com.frugalmechanic" %% "scala-optparse" % "1.2.0",
  "com.fasterxml.woodstox" % "woodstox-core" % "6.5.0",
  "com.sun.xml.bind" % "jaxb-core" % "2.3.0.1", // JAXB - Needed for Java 9+ since it is no longer automatically available
  "com.sun.xml.bind" % "jaxb-impl" % "2.3.1", // JAXB - Needed for Java 9+ since it is no longer automatically available
  "javax.xml.bind" % "jaxb-api" % "2.3.1", // JAXB - Needed for Java 9+ since it is no longer automatically available
  "javax.activation" % "javax.activation-api" % "1.2.0", // JAXB - Needed for Java 9+ since it is no longer automatically available
  "org.scalatest" %% "scalatest" % "3.2.15" % Test
)

publishTo := sonatypePublishToBundle.value
