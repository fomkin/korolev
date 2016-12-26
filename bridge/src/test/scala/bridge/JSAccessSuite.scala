package bridge

import utest._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
 */
object JSAccessSuite extends TestSuite {

  import utest.framework.ExecutionContext.RunNow

  class TestJSAccess extends JSAccess[Future] {

    var outgoing = List.empty[Seq[Any]]

    def receive(msg: Any*): Unit = {
      val reqId = msg(0).asInstanceOf[Int]
      if (reqId == -1) {
        val callbackId = msg(1).asInstanceOf[String]
        val arg = msg(2)
        fireCallback(callbackId, arg)
      }
      else {
        val isSuccess = msg(1).asInstanceOf[Boolean]
        val res = msg(2)
        resolvePromise(reqId, isSuccess, res)
      }
    }

    def send(args: Seq[Any]): Unit = {
      outgoing ::= args
    }
  }

  val tests = TestSuite {

    "Check packing" - {
      val acc = new TestJSAccess()
      "JSArray" - {
        val arr = acc.array("myArray")
        val res = acc.packArgs(Seq(arr))
        assert(res == Seq("@link:myArray"))
      }
      "JSObj  " - {
        val obj = acc.obj("myObj")
        val res = acc.packArgs(Seq(obj))
        assert(res == Seq("@link:myObj"))
      }
      "String " - {
        val s = "hello"
        val res = acc.packArgs(Seq(s))
        assert(res == Seq("hello"))
      }
      "Float  " - {
        val s = 0.1f
        val res = acc.packArgs(Seq(s))
        assert(res == Seq(0.1f))
      }
      "Double " - {
        val s = 0.1d
        val res = acc.packArgs(Seq(s))
        assert(res == Seq(0.1d))
      }
      "Hook.Success" - {
        val res = acc.packArgs(Seq(Hook.Success))
        assert(res == Seq("@hook_success"))
      }
      "Hook.Failure" - {
        val res = acc.packArgs(Seq(Hook.Failure))
        assert(res == Seq("@hook_failure"))
      }
    }

    "Check unpacking" - {
      val acc = new TestJSAccess()
      "JSObj  " - {
        val arg = "@obj:myObj"
        val res: JSObj[Future] = acc.unpackArg(arg)
        assert(!res.isInstanceOf[JSArray[Future]])
        assert(res.id == "myObj")
      }
      "JSArray" - {
        val arg = "@arr:myArray"
        val res: JSArray[Future] = acc.unpackArg(arg)
        assert(res.id == "myArray")
      }
      "Unit   " - {
        val arg = "@unit"
        acc.unpackArg[Unit](arg)
      }
      "String " - {
        val arg = "i am cow"
        val res = acc.unpackArg[String](arg)
        assert(res == "i am cow")
      }
      "Null " - {
        val res = acc.unpackArg[String](null)
        assert(res == null)
      }
    }

    "Check request" - {

      "Success" - {
        val acc = new TestJSAccess()
        val req = acc.request[Float]("get", acc.obj("myObj"), "width")
        var calls = 0
        req.onComplete {
          case Success(x) ⇒
            assert(x == 100f)
            calls += 1
          case Failure(ex) ⇒
            throw ex
        }
        acc.receive(0, true, 100f)
        assert(calls == 1)
      }

      "Failure" - {
        val acc = new TestJSAccess()
        val req = acc.request[Float]("get", acc.obj("myObj"), "width")
        var calls = 0
        req.onFailure {
          case err ⇒
            assert(err.getMessage == "error")
            calls += 1
        }
        acc.receive(0, false, "error")
        assert(calls == 1)
      }
    }
  }
}
