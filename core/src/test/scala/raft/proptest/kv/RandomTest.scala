package raft
package proptest
package kv

import cats.effect._
import cats.effect.concurrent.Ref
import cats.{ Eq, Monad, Parallel }
import org.specs2.Specification
import org.specs2.execute.Result
import org.specs2.specification.core.SpecStructure
import raft.proptest.checker._
import raft.proptest.cluster._
import raft.proptest.kv.KVOps.{ KVCmd, KVEvent }

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Product",
    "org.wartremover.warts.Serializable",
    "org.wartremover.warts.OptionPartial"
  )
)
class RandomTest extends Specification {
  override def is: SpecStructure =
    s2"""
      KVStore should be linearizable - $testRun
      """

  private implicit val cs: ContextShift[IO]       = IO.contextShift(global)
  private implicit val timer: Timer[IO]           = IO.timer(global)
  private implicit val kvEq: Eq[KVResult[String]] = Eq.fromUniversalEquals[KVResult[String]]

  private implicit val opsGen = KVOps.gen(30)

  private val parFactor = 10

  private def testRun = {
    val idStateMachines = List("0", "1", "2")
      .traverse { id =>
        for {
          ref <- Ref[IO].of(Map.empty[String, String])
        } yield (id, KVOps.stateMachine(ref))
      }
      .map(_.toMap)

    val model = new KVDistributedModel[IO]

    def multiThreadRun(opsPerThread: List[(String, List[KVOps[String]])]): IO[List[KVEvent]] = {
      for {
        pairs <- idStateMachines
        clusterState <- Ref[IO].of(
                         ClusterState[IO](
                           Set.empty,
                           Set.empty,
                           Map.empty
                         )
                       )
        allNodes <- setupCluster(pairs)
        clusterOps = ClusterManager(allNodes, clusterState)
        _ <- clusterOps.start
        results <- opsPerThread.parFlatTraverse {
                   case (threadId, ops) =>
                     ops.traverse(
                       op =>
                         KVOps
                           .execute[IO](
                             ops       = op,
                             cluster   = allNodes.mapValues(_.api),
                             threadId  = threadId,
                             sleepTime = 300.millis,
                             timeout   = 3.seconds
                         )
                     )
                 }
        _ <- clusterOps.stop
      } yield {
        results.flatten
      }
    }

    val results = parTest(parFactor) { () =>
      val ops1 = opsGen.sample.get
      val ops2 = opsGen.sample.get
      multiThreadRun(List("001" -> ops1, "002" -> ops2)).flatMap { combined =>
        val history = History.fromList[IO, KVCmd, KVResult[String]](combined)
        LinearizationCheck
          .wingAndGongUnsafe(history, model, Map.empty[String, String])
      }
    }.unsafeRunTimed(15.seconds).get

    Result.forall(results) {
      case Linearizable(_) => success
      case NonLinearizable(longestAttempt, failed, exp, act) =>
        failure(s"Failed to linearize, longestStreak = $longestAttempt, failed at $failed, expect $exp, got $act")
    }
  }

  // todo: Move to package object
  private def parTest[F[_]: Monad: Parallel, FF[_], R](
    n: Int
  )(fn: () => F[R]): F[List[R]] = {
    (0 to n).toList.parTraverse { _ =>
      fn()
    }
  }

}
