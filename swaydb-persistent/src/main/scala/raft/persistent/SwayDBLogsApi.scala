package raft
package persistent

import cats.Functor
import raft.algebra.io.LogsApi
import raft.model.RaftLog

class SwayDBLogsApi[F[_]: Functor, Cmd](db: swaydb.Map[Int, RaftLog[Cmd], Nothing, F]) extends LogsApi[F, Cmd] {

  override def getByIdx(idx: Int): F[Option[Log]] = db.get(idx)

  override def overwrite(logs: Seq[Log]): F[Unit] = {
    db.put(logs.map(s => s.idx -> s)).map(_ => ())
  }

  override def lastLog: F[Option[Log]] = db.lastOption.map { opt =>
    opt.map(_._2)
  }

  /**
    * @param idx - inclusive
    */
  override def takeFrom(idx: Int): F[Seq[Log]] = {
    implicit val bag = db.bag
    db.fromOrAfter(idx).stream.map(_._2).materialize.map(_.toSeq)
  }

  override def append(log: Log): F[Unit] = db.put(log.idx, log).map(_ => ())
}
