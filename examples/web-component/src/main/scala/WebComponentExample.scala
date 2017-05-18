import korolev._
import korolev.server.{KorolevServiceConfig, ServerRouter, StateStorage, _}
import korolev.blazeServer.{KorolevBlazeServer, _}
import korolev.execution._

import scala.concurrent.Future

object WebComponentExample extends KorolevBlazeServer {

  import State.effects._

  def setLatLon(lat: Double, lon: Double) =
    immediateTransition { case s =>
      s.copy(lat = lat, lon = lon)
    }

  val service = blazeService[Future, State, Any] from KorolevServiceConfig [Future, State, Any] (
    serverRouter = ServerRouter.empty[Future, State],
    stateStorage = StateStorage.default(State()),
    head = 'head(
      'script('src /= "https://cdnjs.cloudflare.com/ajax/libs/webcomponentsjs/0.7.24/webcomponents-lite.min.js"),
      'link('rel /= "import", 'href /= "https://leaflet-extras.github.io/leaflet-map/bower_components/leaflet-map/leaflet-map.html"),
      'link('rel /= "import", 'href /= "/multiselect-web-component-master/src/multiselect.html")
    ),
    render = {
      case state =>
        'body(
          'div(
            'button("Click to load web component", event('click){

              immediateTransition { case s => {
                s.copy(showIt = true)
              }}
            })
          ),
          'xMultiselect('placeholder /= "WORKS: Select Value",
            'li('value /= "1", "Item 1"),
            'li('value /= "2", 'selected /= "", "Item 2"),
            'li('value /= "3", "Item 3")),

          if (!state.showIt) 'div() else
          'xMultiselect('placeholder /= "DOES NOT LOAD CORRECTLY: Select Value",
            'li('value /= "1", "Item 1"),
            'li('value /= "2", 'selected /= "", "Item 2"),
            'li('value /= "3", "Item 3"))
        )
    }
  )

}

case class State(lon: Double = 0, lat: Double = 0, showIt: Boolean = false)

object State {
  val effects = Effects[Future, State, Any]
}

