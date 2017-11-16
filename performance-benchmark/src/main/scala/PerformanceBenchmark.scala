import KorolevConnection.{FromServer, ToServer}
import ScenarioExecutor.{Scenario, ScenarioStep}
import akka.actor.ActorSystem
import akka.typed.scaladsl.adapter._

object PerformanceBenchmark extends App {

  val scenarioSteps = Vector(
    ScenarioStep.Expect(None, FromServer.Procedure(0, List(0 ))),
    ScenarioStep.Expect(None, FromServer.Procedure(2, List("click",false ))),
    ScenarioStep.Send(None, ToServer.Callback(0, "0:1_3_1_1:click")),
    ScenarioStep.Expect(None, FromServer.Procedure(6, List("/tab1" ))),
    ScenarioStep.Expect(None, FromServer.Procedure(4, List(3,"1_3_1_1",0,"class","checkbox checkbox__checked",false,0,"1_3_1","1_3_1_2",0,"strike",1,"1_3_1_2","1_3_1_2_1","This is TODO #0" ))),
    ScenarioStep.Expect(None, FromServer.Procedure(0, List(1 ))),
    ScenarioStep.Send(None, ToServer.Callback(0, "1:1_3_6_1:click")),
    ScenarioStep.Expect(None, FromServer.Procedure(6, List("/tab1" ))),
    ScenarioStep.Expect(None, FromServer.Procedure(4, List(3,"1_3_6_1",0,"class","checkbox checkbox__checked",false,0,"1_3_6","1_3_6_2",0,"strike",1,"1_3_6_2","1_3_6_2_1","This is TODO #5" ))),
    ScenarioStep.Expect(None, FromServer.Procedure(0, List(2 ))),
    ScenarioStep.Send(None, ToServer.Callback(0, "2:1_2_2:click")),
    ScenarioStep.Expect(None, FromServer.Procedure(6, List("/tab2" ))),
    ScenarioStep.Expect(None, FromServer.Procedure(4, List(1,"1_2_1","1_2_1_1","Tab1",0,"1_2_2","1_2_2_1",0,"strong",1,"1_2_2_1","1_2_2_1_1","Tab2",3,"1_3_1_1",0,"class","checkbox",false,0,"1_3_1","1_3_1_2",0,"span",1,"1_3_1_2","1_3_1_2_1","This is TODO #0",3,"1_3_6_1",0,"class","checkbox",false,0,"1_3_6","1_3_6_2",0,"span",1,"1_3_6_2","1_3_6_2_1","This is TODO #5",0,"1_3","1_3_7",0,"div",0,"1_3_7","1_3_7_1",0,"div",3,"1_3_7_1",0,"class","checkbox",false,0,"1_3_7","1_3_7_2",0,"span",1,"1_3_7_2","1_3_7_2_1","This is TODO #6",0,"1_3","1_3_8",0,"div",0,"1_3_8","1_3_8_1",0,"div",3,"1_3_8_1",0,"class","checkbox",false,0,"1_3_8","1_3_8_2",0,"span",1,"1_3_8_2","1_3_8_2_1","This is TODO #7" ))),
    ScenarioStep.Expect(None, FromServer.Procedure(0, List(3 ))),
    ScenarioStep.Send(None, ToServer.Callback(0, "3:1_3_2_1:click")),
    ScenarioStep.Expect(None, FromServer.Procedure(6, List("/tab2" ))),
    ScenarioStep.Expect(None, FromServer.Procedure(4, List(3,"1_3_2_1",0,"class","checkbox checkbox__checked",false,0,"1_3_2","1_3_2_2",0,"strike",1,"1_3_2_2","1_3_2_2_1","This is TODO #1" ))),
    ScenarioStep.Expect(None, FromServer.Procedure(0, List(4 )))
  )

  val scenario = Scenario("hello world", scenarioSteps)
  
  val guardian = {
    import akka.typed.scaladsl.Actor

    Actor.deferred[KorolevConnection.ToServer] { ctx =>
      KorolevConnection("localhost", 8080, None, ssl = false) {
        ctx.spawnAnonymous {
          ScenarioExecutor(scenario) {
            ctx.spawnAnonymous {
              Actor.immutable[ScenarioExecutor.Report] { (_, message) =>
                println(message)
                Actor.same
              }
            }
          }
        }
      }
    }
  }

  val system = ActorSystem()
  system.spawn(guardian, "connection")
}
