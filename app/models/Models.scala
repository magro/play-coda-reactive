package models

import java.util.{ Date }
import play.api.db._
import play.api.Play.current
import anorm._
import anorm.SqlParser._
import scala.concurrent.Future
import com.github.mauricio.async.db.{ RowData, Connection }
import com.github.mauricio.async.db.util.ExecutorServiceUtils.CachedExecutionContext
import play.api.libs.concurrent.Promise
import org.joda.time.DateTime
import org.joda.time.LocalDate

case class Company(id: Option[Long] = None, name: String)
case class Computer(id: Option[Long] = None, name: String, introduced: Option[Date], discontinued: Option[Date], companyId: Option[Long])

/**
 * Helper for pagination.
 */
case class Page[A](items: Seq[A], page: Int, offset: Long, total: Long) {
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}

object ComputerRepository {

  private def simple(row: RowData): Computer = {
    Computer(
      id = Some(row("id").asInstanceOf[Long]),
      name = row("name").asInstanceOf[String],
      introduced = Option(row("introduced").asInstanceOf[LocalDate]).map(_.toDate()),
      discontinued = Option(row("discontinued").asInstanceOf[LocalDate]).map(_.toDate()),
      companyId = Option(row("company_id").asInstanceOf[Long]))
  }

  private def withCompany(row: RowData): (Computer, Option[Company]) = {
    (simple(row), Option(Company.simple(row)))
  }

}

class ComputerRepository(pool: Connection) {

  import ComputerRepository._

  // -- Generic Queries
  
  def findOne[T](stmt: String, params: Seq[Any] = List(), mapper: (RowData) => T): Future[Option[T]] = {
    pool.sendPreparedStatement(stmt, params).map {
      _.rows.map(rs => mapper(rs(0)))
    }
  }
  
  def find[T](stmt: String, params: Seq[Any] = List(), mapper: (RowData) => T): Future[IndexedSeq[T]] = {
    pool.sendPreparedStatement(stmt, params).map(_.rows.get.map(item => mapper(item)))
  }
  
  /**
   * Expects a query that returns the count as first column of the resulting row.
   */
  def count[T](stmt: String, params: Seq[Any] = List()): Future[Long] = {
    pool.sendPreparedStatement(stmt, params).map(_.rows.get(0)(0).asInstanceOf[Long])
  }

  // -- Queries

  /**
   * Retrieve a computer from the id.
   */
  def findById(id: Long): Future[Option[Computer]] = {
    findOne("select * from computer where id = ?", Array(id), simple)
  }

  /**
   * Return a page of (Computer,Company).
   *
   * @param page Page to display
   * @param pageSize Number of computers per page
   * @param orderBy Computer property used for sorting
   * @param filter Filter applied on the name column
   */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%"): Future[Page[(Computer, Option[Company])]] = {

    val offset = pageSize * page

    // order manually, see e.g. http://stackoverflow.com/questions/9778185/anorm-broken-in-postgresql-9-0-selects-with-order-by
    val order = if (orderBy > 0) "asc" else "desc"

    val computers = find(
      """
        select computer.*, company.id as comp_id, company.name as comp_name
        from computer left join company on computer.company_id = company.id
        where computer.name like ?
        order by %d %s nulls last
        limit ? offset ?
      """.format(scala.math.abs(orderBy), order),
      Array[Any](filter, pageSize, offset),
      withCompany)

    val totalRows = count(
      """
        select count(*) from computer 
        left join company on computer.company_id = company.id
        where computer.name like ?
      """,
      Array[Any](filter)
    )
      
    for {
      comps <- computers;
      rows  <- totalRows
    } yield Page(comps, page, offset, rows)

    // As alternative to for comprehension:
//    computers.flatMap(c => totalRows.map(t => Page(c, page, offset, t)))

  }

  /**
   * Update a computer.
   *
   * @param id The computer id
   * @param computer The computer values.
   */
  def update(id: Long, computer: Computer) = {
    pool.sendPreparedStatement(
      """
        update computer
        set name = ?, introduced = ?, discontinued = ?, company_id = ?
        where id = ?
      """, Array(computer.name, computer.introduced, computer.discontinued, computer.companyId, id)
    ).map(_.rowsAffected)
  }

  /**
   * Insert a new computer.
   *
   * @param computer The computer values.
   */
  def insert(computer: Computer) = {
    pool.sendPreparedStatement(
      """
        insert into computer values (
          (select nextval('computer_seq')),
          ?, ?, ?, ?
        )
      """, Array(
        computer.name,
        computer.introduced.map(LocalDate.fromDateFields(_)),
        computer.discontinued.map(LocalDate.fromDateFields(_)),
        computer.companyId)
    ).map {
      queryResult => computer.copy(id = Some(queryResult.rows.get(0)("id").asInstanceOf[Long]))
    }
  }

  /**
   * Delete a computer.
   *
   * @param id Id of the computer to delete.
   */
  def delete(id: Long) = {
    pool.sendPreparedStatement("delete from computer where id = ?", Array(id)).map(_.rowsAffected)
  }
  
  private def withTransaction[T](f: (Connection) => Future[T]): Future[T] = {
    pool.connect.flatMap { connection =>
      connection.sendQuery("BEGIN TRANSACTION")
      try {
        val res = f(connection)
        connection.sendQuery("COMMIT")
        res
      } catch {
        case e: Exception => {
          connection.sendQuery("ROLLBACK")
          throw e
        }
      }
    }
  }

}

object Company {

  private[models] def simple(row: RowData): Company = {
    Company(
      id = Some(row("comp_id").asInstanceOf[Long]),
      name = row("comp_name").asInstanceOf[String])
  }

}

class CompanyRepository(pool: Connection) {

  /**
   * Construct the Map[String,String] needed to fill a select options set.
   */
  def options: Future[Seq[(String, String)]] = {
    pool.sendQuery("select id, name from company order by name").map {
      queryResult =>
        queryResult.rows.get.map {
          row => row("id").asInstanceOf[Long].toString -> row("name").asInstanceOf[String]
        }
    }
  }

}

