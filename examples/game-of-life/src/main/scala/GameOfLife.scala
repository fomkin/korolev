import korolev._
import korolev.server._
import korolev.blazeServer._
import korolev.execution._

import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object GameOfLife extends KorolevBlazeServer {

  import Universe.effects._

  val universeSize = 20
  val cellRadius = 10
  val cellGap = 2
  val cellWidth = cellRadius * 2
  val viewSide = cellRadius + universeSize * (cellWidth + cellGap)

  val viewSideS = viewSide.toString
  val cellRadiusS = cellRadius.toString

  val service = blazeService[Future, Universe, Any] from KorolevServiceConfig(
    stateStorage = StateStorage.default(Universe(universeSize)),
    serverRouter = ServerRouter.empty,
    render = {
      case universe =>
        'body(
          'div(
            'input(
              'type /= "button",
              'value /= "Step",
              event('click) {
                immediateTransition { case state =>
                  state.next
                }
              }
            )
          ),
          'svg('width /= viewSideS, 'height /= viewSideS,
            for {
              x <- 0 until universe.size
              y <- 0 until universe.size
            } yield {
                def pos(n: Int) = {
                  val p = cellRadius + n * (cellWidth + cellGap)
                  p.toString
                }
                'circle(
                  'cx /= pos(x),
                  'cy /= pos(y),
                  'r /= cellRadiusS,
                  'fill /= {
                    if (universe(x, y).alive) "#000000"
                    else "#EEEEEE"
                  },
                  // Generate actions when clicking checkboxes
                  event('click) {
                    immediateTransition { case state =>
                      state.check(x, y)
                    }
                  }
                )
            }
          )
        )
    }
  )
}

case class Universe(cells: Vector[Universe.Cell], size: Int) {

  import Universe._

  import scala.annotation.tailrec

  private def index(x: Int, y: Int) = {
    // Torus
    (x % size + size) % size +
    (y % size + size) % size * size
  }

  /**
    * Get a cell in (x, y)
    * @return the cell
    */
  def apply(x: Int, y: Int): Cell = cells(index(x, y))

  /**
    * Kill or resurrect a cell in (x, y)
    * @return Modified universe
    */
  def check(x: Int, y: Int): Universe = {
    val i = index(x, y)
    val cell = cells(i)
    copy(cells = cells.updated(i, cell.copy(alive = !cell.alive)))
  }

  /**
    * Create a new generation of universe
    */
  def next: Universe = {

    def aliveNeighbors(px: Int, py: Int): Int = {
      @tailrec def aux(num: Int = 0, x: Int = px + 1, y: Int = py + 1): Int = {
        if (y < py - 1) num
        else if (x < px - 1) aux(num, y = y - 1)
        else if (x == px && y == py) aux(num, x - 1, y)
        else if (apply(x, y).alive) aux(num + 1, x - 1, y)
        else aux(num, x - 1, y)
      }
      aux()
    }

    def mustResurrect(x: Int, y: Int) = aliveNeighbors(x, y) == 3

    def mustDie(x: Int, y: Int) = {
      val n = aliveNeighbors(x, y)
      n < 2 || n > 3
    }

    val updated = cells map {
      case p @ Cell(x, y, false) if mustResurrect(x, y) => p.copy(alive = true)
      case p @ Cell(x, y, true) if mustDie(x, y) => p.copy(alive = false)
      case p => p
    }
    copy(cells = updated)
  }
}

object Universe {

  val effects = Effects[Future, Universe, Any]

  case class Cell(x: Int, y: Int, alive: Boolean)

  def apply(size: Int): Universe = {
    val cells = for (y <- 0 until size; x <- 0 until size)
      yield Cell(x, y, alive = false)
    Universe(cells.toVector, size)
  }
}

