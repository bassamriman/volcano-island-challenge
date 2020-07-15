package com.rimanware.volcanoisland.common;

import akka.actor.Props;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.testkit.TestRoute;

public final class RequesterTestActor extends LoggingReceiveActor {
  private final TestRoute route;
  private final HttpRequest request;

  private RequesterTestActor(final TestRoute route, final HttpRequest request) {
    this.route = route;
    this.request = request;
  }

  private static RequesterTestActor create(final TestRoute route, final HttpRequest request) {
    return new RequesterTestActor(route, request);
  }

  public static Props props(final TestRoute route, final HttpRequest request) {
    return Props.create(RequesterTestActor.class, () -> RequesterTestActor.create(route, request));
  }

  public static Start start() {
    return Start.INSTANCE;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            RequesterTestActor.Start.class,
            start -> {
              sender().tell(route.run(request), self());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  enum Start {
    INSTANCE;

    Start() {}

    @Override
    public String toString() {
      return "Start{}";
    }
  }
}
