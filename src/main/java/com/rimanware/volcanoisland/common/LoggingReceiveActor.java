package com.rimanware.volcanoisland.common;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

public abstract class LoggingReceiveActor extends AbstractActor {

  protected final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  @Override
  public void aroundReceive(final PartialFunction<Object, BoxedUnit> receive, final Object msg) {
    log.info("Received Message : {}", msg);
    super.aroundReceive(receive, msg);
  }
}
