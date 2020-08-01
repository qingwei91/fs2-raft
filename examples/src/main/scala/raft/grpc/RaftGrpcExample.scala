package raft.grpc

import java.nio.file.Paths

import cats.effect.concurrent.{ MVar, Ref }
import cats.effect.{ ExitCode, IO, IOApp }

import io.grpc.ManagedChannelBuilder

import raft.{ RaftProcess, RawConfig }
import pureconfig.generic.auto._
import raft.algebra.StateMachine
import raft.debug.Slf4jLogger
import raft.grpc.command.{ Count, Increment }
import raft.grpc.server.rpc.PeerRPCGrpc
import raft.model.{ ClusterConfig, Metadata, RaftLog }
import raft.persistent.{ SwayDBLogsApi, SwayDBPersist }
import swaydb.data.slice.Slice
import swaydb.serializers.Default._
import swaydb.serializers.Serializer

import scala.concurrent.ExecutionContext.Implicits.global

import raft.Implicits._

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Any"
  )
)
object RaftGrpcExample extends IOApp {

  implicit val IncrementEQ = cats.Eq.fromUniversalEquals[Increment]

  implicit object raftLogSerializer extends Serializer[RaftLog[Increment]] {
    override def write(data: RaftLog[Increment]): Slice[Byte] = {
      Slice
        .create[Byte](100)
        .addInt(data.idx)
        .addInt(data.term)
        .addInt(data.command.count)
        .close()
    }

    override def read(data: Slice[Byte]): RaftLog[Increment] = {
      val reader = data.createReader()
      val idx    = reader.readInt()
      val term   = reader.readInt()
      val i      = reader.readInt()
      RaftLog(idx, term, Increment(i))
    }
  }

  val nodeId = sys.env.getOrElse("NODE_ID", "1")
  val networkConfs = {
    val loaded   = pureconfig.loadConfig[List[RawConfig]]("nodes")
    val allConfs = loaded.getOrElse(Nil)
    allConfs.map { rc =>
      rc.id -> rc.uri
    }.toMap
  }

  val counter: IO[StateMachine[IO, Increment, Count]] = for {
    state <- Ref[IO].of(Count(0))
  } yield {
    new StateMachine[IO, Increment, Count] {
      override def execute(cmd: Increment): IO[Count] = {
        state.modify { i =>
          val j = Count(i.count + cmd.count)
          j -> j
        }
      }

      override def getCurrent: IO[Count] = state.get
    }
  }

  private implicit val bag = swaydb.cats.effect.Bag.apply

  private def logDB: IO[SwayDBLogsApi[IO, Increment]] = {
    val dbPath = Paths.get("raft-sample-log")
    val db     = swaydb.persistent.Map[Int, RaftLog[Increment], Nothing, IO](dbPath)
    db.map { inner =>
      new SwayDBLogsApi(inner)
    }
  }

  private def metadataDB: IO[SwayDBPersist[IO]] = {
    val dbPath = Paths.get("raft-sample-persist")
    val db     = swaydb.persistent.Map[Int, Metadata, Nothing, IO](dbPath)

    for {
      swayMap <- db
      _       <- swayMap.put(1, Metadata.init)
      lock    <- MVar[IO].of(())
    } yield {
      new SwayDBPersist(swayMap, lock)
    }
  }

  val config = ClusterConfig(nodeId, networkConfs.keySet - nodeId)
  val grpcClients = config.peersId.map { nid =>
    val channel = ManagedChannelBuilder.forTarget(networkConfs(nid).toString()).usePlaintext().build
    nid -> PeerRPCGrpc.stub(channel)
  }.toMap

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      sm   <- counter
      logs <- logDB
      meta <- metadataDB
      network = new GrpcNetwork[IO](grpcClients)
      logger  = new Slf4jLogger[IO, Increment, Count]
      proc <- RaftProcess.init[IO, Increment, Count](sm, config, logs, network, logger, meta)
      code <- proc.startRaft
               .use(_.compile.drain.map(_ => ExitCode.Success))
    } yield {
      code
    }
  }

}
