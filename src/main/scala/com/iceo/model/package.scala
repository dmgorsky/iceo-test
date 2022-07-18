package com.iceo

package object model {

  case class Message(id: Option[Long], message: String)
  case object MsgNotFoundError
}
