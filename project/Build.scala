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
        "com.github.seratch"  %% "scalikejdbc" % "[1.6.9,)",
        "com.github.seratch"  %% "scalikejdbc-async" % "[0.2,)",
        "com.github.seratch"  %% "scalikejdbc-async-play-plugin" % "[0.2,)",
        "com.github.mauricio" %% "postgresql-async"  % "[0.2,)",
        "org.slf4j"           %  "slf4j-simple"      % "[1.7,)" // slf4j implementation
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      // Add your own project settings here      
    )

}
            
