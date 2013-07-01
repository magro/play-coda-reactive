import play.api.{ Application, GlobalSettings }
import com.github.mauricio.async.db.pool.{ PoolConfiguration, ConnectionPool }
import com.github.mauricio.async.db.postgresql.util.URLParser
import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory

import models.ComputerRepository
import models.CompanyRepository

object Global extends GlobalSettings {

  var pool: ConnectionPool[_] = _
  var appController: controllers.Application = _

  override def onStart(app: Application) {
    val dbConfig = getDbConfig(app)
    val factory = new PostgreSQLConnectionFactory(dbConfig)
    pool = new ConnectionPool(factory, PoolConfiguration.Default)
    appController = new controllers.Application(new ComputerRepository(pool), new CompanyRepository(pool))
  }

  private def config(key: String)(implicit app: Application): String = {
    app.configuration.getString(key).getOrElse(throw new RuntimeException("No db.default.url configured"))
  }

  override def getControllerInstance[A](clazz: Class[A]): A = {
    // simple and stupid
    if (clazz == classOf[controllers.Application])
      appController.asInstanceOf[A]
    else
      throw new RuntimeException(s"Controller of class $clazz not supported")
  }

  override def onStop(app: Application) {
    pool.close
  }

  private def getDbConfig(implicit app: Application): Configuration = {
    System.getenv("DATABASE_URL") match {
      case url: String => URLParser.parse(url)
      case _ => {
        // maybe the url already has set the username and password or not.
        var dbConfig = URLParser.parse(config("db.default.url"))
        app.configuration.getString("db.default.user").foreach { user =>
          dbConfig = dbConfig.copy(username = user)
        }
        app.configuration.getString("db.default.password").foreach { pwd =>
          dbConfig = dbConfig.copy(password = Some(pwd))
        }
        dbConfig
      }
    }
  }

}