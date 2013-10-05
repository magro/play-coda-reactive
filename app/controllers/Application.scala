package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import anorm._
import views._
import models._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.Date

/**
 * Manage a database of computers
 */
object Application extends Controller { 
  
  /**
   * This result directly redirect to the application home.
   */
  val Home = Redirect(routes.Application.list(0, 2, ""))
  
  /**
   * Describe the computer form (used in both edit and create screens).
   */
  val computerForm = Form(
    mapping(
      "id" -> ignored(None: Option[Long]),
      "name" -> nonEmptyText,
      "introduced" -> optional(jodaDate("yyyy-MM-dd")),
      "discontinued" -> optional(date("yyyy-MM-dd")),
      "company" -> optional(longNumber)
    )(Computer.apply)(Computer.unapply)
  )
  
  // -- Actions

  /**
   * Handle default path requests, redirect to computers list
   */  
  def index = Action { Home }
  
  /**
   * Display the paginated list of computers.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param filter Filter applied on computer names
   */
  def list(page: Int, orderBy: Int, filter: String) = Action { implicit request =>
    Async {
      Computer.list(page = page, orderBy = orderBy, filter = ("%" + filter + "%")).map { items =>
        Ok(html.list(items, orderBy, filter))
      }
    }
  }
  
  /**
   * Display the 'edit form' of a existing Computer.
   *
   * @param id Id of the computer to edit
   */
  def edit(id: Long) = Action {
    val computerFO = Computer.findById(id)
    val companyOptionsF = Company.options
    Async {
      for {
        computer <- computerFO;
        companyOptions <- companyOptionsF
      } yield {
        computer.map { c =>
          Ok(html.editForm(id, computerForm.fill(c), companyOptions))
        }.getOrElse(NotFound)
      }
    }
  }
  
  /**
   * Handle the 'edit form' submission 
   *
   * @param id Id of the computer to edit
   */
  def update(id: Long) = Action { implicit request =>
    withCompanyOptions { companyOptions =>
      computerForm.bindFromRequest.fold(
        formWithErrors => BadRequest(html.editForm(id, formWithErrors, companyOptions)),
        computer => {
          Computer.save(id, computer)
          Home.flashing("success" -> "Computer %s has been updated".format(computer.name))
        })
    }
  }

  private def withCompanyOptions(f: (Seq[(String, String)]) => Result): AsyncResult = {
    Async {
      Company.options.map(f(_))
    }
  }
  
  /**
   * Display the 'new computer form'.
   */
  def create = Action {
    withCompanyOptions(companyOptions => Ok(html.createForm(computerForm, companyOptions)))
  }
  
  /**
   * Handle the 'new computer form' submission.
   */
  def save = Action { implicit request =>
      computerForm.bindFromRequest.fold(
        formWithErrors => withCompanyOptions(companyOptions => BadRequest(html.createForm(formWithErrors, companyOptions))),
        computer => Async {
          Computer.create(computer.name, computer.introduced, computer.discontinued, computer.companyId).map { c =>
            Home.flashing("success" -> "Computer %s has been created".format(computer.name))
          }
        })
  }
  
  /**
   * Handle computer deletion.
   */
  def delete(id: Long) = Action {
    Async {
      Computer.findById(id).map{
        case Some(computer) => {
          computer.destroy()
          Home.flashing("success" -> "Computer %s has been updated".format(computer.name))
        }
        case None => NotFound
      }
    }
  }

}
            
