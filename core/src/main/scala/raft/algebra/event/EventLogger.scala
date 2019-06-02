package raft.algebra.event

import raft.model._

trait EventLogger[F[_], Cmd, State] {
  def receivedClientCmd(cmd: Cmd): F[Unit]
  def replyClientWriteReq(req: Cmd, res: WriteResponse): F[Unit]

  def receivedClientRead: F[Unit]
  def replyClientRead(res: ReadResponse[State]): F[Unit]

  def electionStarted(term: Int, lastLogIdx: Int): F[Unit]

  def candidateReceivedVote(voteRequest: VoteRequest, peerId: String): F[Unit]
  def candidateVoteRejected(voteRequest: VoteRequest, peerId: String): F[Unit]

  def grantedVote(voteRequest: VoteRequest): F[Unit]
  def rejectedVote(voteRequest: VoteRequest): F[Unit]

  def elected(term: Int, lastLog: Option[Int]): F[Unit]

  def replicationStarted(term: Int): F[Unit]

  def leaderAppendSucceeded(appendRequest: AppendRequest[Cmd], followerId: String): F[Unit]
  def leaderAppendRejected(appendRequest: AppendRequest[Cmd], followerId: String): F[Unit]

  def acceptedLog(appendRequest: AppendRequest[Cmd], state: RaftNodeState[F, Cmd]): F[Unit]

  def rejectedLog(appendRequest: AppendRequest[Cmd], state: RaftNodeState[F, Cmd]): F[Unit]

  def logCommitted(idx: Int, cmd: Cmd): F[Unit]
  def stateUpdated(state: State): F[Unit]

  def errorLogs(message: String): F[Unit]
}
