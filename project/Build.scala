import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "play-coda-reactive"
    val appVersion      = "1.0"

    val appDependencies = Seq(
    	jdbc,
    	anorm,
    	// for evolutions
    	"postgresql" % "postgresql" % "9.1-901-1.jdbc4",
        "com.github.mauricio" %% "postgresql-async" % "0.2.3.1"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      // Add your own project settings here  
      // resolvers += Resolver.file("localIvy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)
    )

}
            
