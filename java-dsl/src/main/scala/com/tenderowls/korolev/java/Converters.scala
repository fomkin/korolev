package com.tenderowls.korolev.java

import levsha.events.{EventPhase => ScalaEventPhase}
import effects.{EventPhase => JavaEventPhase}

object Converters {
  def eventPhase(value: JavaEventPhase): ScalaEventPhase = value match {
    case JavaEventPhase.Capturing => ScalaEventPhase.Capturing
    case JavaEventPhase.AtTarget => ScalaEventPhase.AtTarget
    case JavaEventPhase.Bubbling => ScalaEventPhase.Bubbling
  }
}
