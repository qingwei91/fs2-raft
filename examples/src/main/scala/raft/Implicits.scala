package raft

import org.http4s.Uri
import pureconfig.ConfigReader
import raft.model.Metadata
import swaydb.data.slice.Slice
import swaydb.serializers.Serializer

object Implicits {
  implicit val readURI = ConfigReader[String].map(Uri.unsafeFromString)
  implicit object persistSerializer extends Serializer[Metadata] {
    override def write(data: Metadata): Slice[Byte] =
      Slice.create[Byte](100).addInt(data.currentTerm).addString(data.votedFor.getOrElse(""))

    override def read(data: Slice[Byte]): Metadata = {
      val reader   = data.createReader()
      val currentT = reader.readInt()
      val votedRaw = reader.readRemainingAsString()
      val votedFor = if (votedRaw == "") None else Some(votedRaw)
      Metadata(currentT, votedFor)
    }
  }
}
