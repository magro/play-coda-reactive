package models

import java.util.{Date}

import play.api.db._
import play.api.Play.current

import scalikejdbc._, async._, FutureImplicits._, SQLInterpolation._
import scala.concurrent._

case class Company(id: Long, name: String) extends ShortenedNames
case class Computer(id: Option[Long] = None, name: String, introduced: Option[Date], discontinued: Option[Date], companyId: Option[Long]) extends ShortenedNames {

  def save()(implicit session: AsyncDBSession = AsyncDB.sharedSession, cxt: EC = ECGlobal): Future[Computer] = Computer.save(id.get, this)(session, cxt)
  def destroy()(implicit session: AsyncDBSession = AsyncDB.sharedSession, cxt: EC = ECGlobal): Future[Int] = Computer.destroy(id.get)(session, cxt)
  
}

/**
 * Helper for pagination.
 */
case class Page[A](items: Seq[A], page: Int, offset: Long, total: Long) {
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}

object Computer extends SQLSyntaxSupport[Computer] with ShortenedNames {

  override val columnNames = Seq("id", "name", "introduced", "discontinued", "company_id")

  def apply(c: SyntaxProvider[Computer])(rs: WrappedResultSet): Computer = apply(c.resultName)(rs)
  def apply(c: ResultName[Computer])(rs: WrappedResultSet): Computer = new Computer(
    id = rs.longOpt(c.id),
    name = rs.string(c.name),
    introduced = rs.dateOpt(c.introduced),
    discontinued = rs.dateOpt(c.discontinued),
    companyId = rs.longOpt(c.companyId)
  )

  // join query with company table
  def apply(cuterSP: SyntaxProvider[Computer], canySP: SyntaxProvider[Company])(rs: WrappedResultSet): (Computer, Option[Company]) = {
    (apply(cuterSP.resultName)(rs), Some(Company(canySP)(rs)))
  }

  private val cuter = Computer.syntax("computer")
  private val cany = Company.c
  
  // -- Queries
  
  /**
   * Retrieve a computer from the id.
   */
  def findById(id: Long)(implicit session: AsyncDBSession = AsyncDB.sharedSession, cxt: EC = ECGlobal): Future[Option[Computer]] = withSQL {
    select.from(Computer as cuter).where.eq(cuter.id, id)
  }.map(Computer(cuter))

  
  /**
   * Return a page of (Computer,Company).
   *
   * @param page Page to display
   * @param pageSize Number of computers per page
   * @param orderBy Computer property used for sorting
   * @param filter Filter applied on the name column
   */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%")
  	(implicit session: AsyncDBSession = AsyncDB.sharedSession, cxt: EC = ECGlobal): Future[Page[(Computer, Option[Company])]] = {
    
    val offset = pageSize * page
    
    val computers = withSQL {
      select(cuter.*, cany.*).from(Computer as cuter).leftJoin(Company as cany).on(cuter.companyId, cany.id)
      .where.like(cuter.name, filter)
      .orderBy(cuter.id) // TODO: dynamic order?
      .limit(pageSize).offset(offset)
    }.map(Computer(cuter, cany)).list().future()
    
    val totalRows = withSQL {
	    select(sqls.count).from(Computer as cuter).leftJoin(Company as cany).on(cuter.companyId, cany.id)
	    .where.like(cuter.name, filter)
	  }.map(rs => rs.long(1)).single.future.map(_.get)
	  
   for {
     comps <- computers;
     rows  <- totalRows
   } yield Page(comps, page, offset, rows)
    
  }
  
  /**
   * Update a computer.
   *
   * @param id The computer id
   * @param computer The computer values.
   */
  def save(id: Long, c: Computer)(implicit session: AsyncDBSession = AsyncDB.sharedSession, cxt: EC = ECGlobal): Future[Computer] = {
    withSQL {
      update(Computer).set(
        column.name -> c.name,
        column.introduced -> c.introduced,
        column.discontinued -> c.discontinued,
        column.companyId -> c.companyId
      ).where.eq(column.id, c.id)
    }.update.future.map(_ => c)
  }
  
  /**
   * Insert a new computer.
   */
  def create(name: String, introduced: Option[Date] = None, discontinued: Option[Date] = None, companyId: Option[Long] = None)(
    implicit session: AsyncDBSession = AsyncDB.sharedSession, cxt: EC = ECGlobal): Future[Computer] = {
    for {
      id <- withSQL {
        insert.into(Computer).namedValues(
          column.name -> name,
          column.introduced -> introduced,
          column.discontinued -> discontinued,
          column.companyId -> companyId)
          .returningId // if you run this example for MySQL, please remove this line
      }.updateAndReturnGeneratedKey()
    } yield Computer(id = Some(id), name = name, introduced = introduced, discontinued = discontinued, companyId = companyId)
  }
  
  /**
   * Delete a computer.
   *
   * @param id Id of the computer to delete.
   */
  def destroy(id: Long)(implicit session: AsyncDBSession = AsyncDB.sharedSession, cxt: EC = ECGlobal): Future[Int] = {
    delete.from(Computer).where.eq(cuter.id, id)
  }
  
}

object Company extends SQLSyntaxSupport[Company] with ShortenedNames {
  
  override val columnNames = Seq("id", "name")

  private[models] val c = Company.syntax("company")

  def apply(c: SyntaxProvider[Company])(rs: WrappedResultSet): Company = apply(c.resultName)(rs)
  def apply(c: ResultName[Company])(rs: WrappedResultSet): Company = new Company(
    id = rs.long(c.id),
    name = rs.string(c.name)
  )
  
  /**
   * Construct the Map[String,String] needed to fill a select options set.
   */
  def options()(implicit session: AsyncDBSession = AsyncDB.sharedSession, cxt: EC = ECGlobal): Future[Seq[(String,String)]] = {
    sql"select ${c.result.*} from ${Company as c}".map(Company(c.resultName)).list.future().map{ comps => comps.map(comp => (comp.id.toString(), comp.name))}
  }
  /*withSQL {
    select.from(Company as c).orderBy(c.name)
  }.map(Company(c)).list().map{ rs => (rs.long(c.id).toString(), rs.string(c.name)) }
  */
  // .map(x => (x.long(1), x.string(2)).list
  // .map(foo => (rs: WrappedResultSet) => (rs.long(c.id).toString(), rs.string(c.name)))
  
}

