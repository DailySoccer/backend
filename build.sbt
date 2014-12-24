//
// Sbt es enrevesado. Para poder tocar y entenderlo bien hace falta leerse el tutorial completo!
//
// Las siguientes URLs no apuntan a la ultima version desde el upgrade a Play 2.3.4:
//
// Todas las keys: http://www.scala-sbt.org/0.13/sxr/sbt/Keys.scala.html
// Y sus defaults: http://www.scala-sbt.org/0.13/sxr/sbt/Defaults.scala.html

name := "backend"

version := "1.0.10"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  cache
  ,filters
  ,javaJdbc
  ,javaWs
  ,"com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.RC2"
  ,"com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.RC2"
)

// Desconectamos la compilacion de documentacion, que nos ralentiza el deploy
sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

// javacOptions ++= Seq("-Xlint:deprecation")

// Hacemos el hook de las rutas hijas (por ejemplo, admin/) dentro del fichero de routas ('backend.routes'), como
// esta documentado que hay que hacerlo, con la sintaxis de flecha "-> /admin admin.routes". Esto genera un warning
// sobre que debemos activar las reflectiveCalls en Scala. Pero como no sabemos las implicaciones de hacer esto,
// preferimos dejarlo sin activar hasta que investiguemos mas y que siga saltando el warning.
//scalacOptions ++= { Seq("-feature", "-language:reflectiveCalls") }
