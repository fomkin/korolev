import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object WebComponentExample extends SimpleAkkaHttpKorolevApp {

  import State.globalContext._
  import symbolDsl._

  private def setLatLon(lat: Double, lon: Double): (Access => EventResult) = {
    (access: Access) => {
      access.transition { case s =>
        s.copy(lat = lat, lon = lon)
      }
    }
  }

  val service = akkaHttpService{
    KorolevServiceConfig [Future, State, Any] (
      router = Router.empty,
      stateStorage = StateStorage.default(State()),
      head = {
        Seq(
          'script('src /= "https://cdnjs.cloudflare.com/ajax/libs/webcomponentsjs/0.7.24/webcomponents-lite.min.js"),
          'link('rel /= "import", 'href /= "https://leaflet-extras.github.io/leaflet-map/bower_components/leaflet-map/leaflet-map.html")
        )
      },
      render = {
        case state =>
          'body (
            'div (
              'button ("San Francisco", event('click)(setLatLon(37.7576793, -122.5076402))),
              'button ("London", event('click)(setLatLon(51.528308, -0.3817983))),
              'button ("New York", event('click)(setLatLon(40.705311, -74.2581908))),
              'button ("Moscow", event('click)(setLatLon(55.748517, 37.0720941))),
              'button ("Korolev", event('click)(setLatLon(55.9226846, 37.7961706)))
            ),
            'leafletMap (
              'width @= 500, 'height @= 300,
              'latitude /= state.lat.toString,
              'longitude /= state.lon.toString,
              'zoom /= "10"
            )
          )
      }
    )
  }

}

case class State(lon: Double = 0, lat: Double = 0)

object State {
  val globalContext = Context[Future, State, Any]
}

