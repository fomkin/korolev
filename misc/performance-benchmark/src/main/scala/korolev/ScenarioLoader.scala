package korolev

import java.io.File

import korolev.data.FromServer.Procedure
import korolev.data.ToServer.Callback
import korolev.data.{Scenario, ScenarioStep}

import scala.concurrent.{ExecutionContext, Future}
import ujson._

object ScenarioLoader {

  final val FromServerArrow = "->"
  final val ToServerArrow = "<-"

  def fromFile(file: File)(implicit executionContext: ExecutionContext): Future[Either[List[(Int, String)], Scenario]] =
    Future {
      val source = io.Source.fromFile(file)
      fromString(source.mkString).map { steps =>
        Scenario(file.getName, steps)
      }
    }.recover {
      case ex =>
        println(ex)
        throw ex
    }

  def fromString(s: String): Either[List[(Int, String)], Vector[ScenarioStep]] = {

    val lines = s
      .split('\n')
      .toList
      .zipWithIndex
      .map {
        case (line, i) if line.indexOf(FromServerArrow) > -1 =>
          val index = line.indexOf(FromServerArrow)
          val source = line.substring(index + FromServerArrow.length)
          val json = read(source)
          Procedure
            .fromJson(json)
            .map(ScenarioStep.Expect(None, _))
            .left
            .map(i -> _)
        case (line, i) if line.indexOf(ToServerArrow) > -1 =>
          val index = line.indexOf(ToServerArrow)
          val source = line.substring(index + ToServerArrow.length)
          val json = read(source)
          Callback
            .fromJson(json)
            .map(value => ScenarioStep.Send(None, value))
            .left
            .map(i -> _)
      }

    def sort(errors: List[(Int, String)],
             steps: List[ScenarioStep],
             lines: List[Either[(Int, String), ScenarioStep]]): (List[(Int, String)], List[ScenarioStep]) = {
      lines match {
        case Left(x) :: xs  => sort(x :: errors, steps, xs)
        case Right(x) :: xs => sort(errors, x :: steps, xs)
        case Nil            => (errors, steps)
      }
    }

    val (errors, stepsReversed) = sort(Nil, Nil, lines)
    if (errors.nonEmpty) Left(errors)
    else Right(stepsReversed.reverse.toVector)
  }
}