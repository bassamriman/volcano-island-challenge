package com.rimanware.volcanoisland.services.requesthandlers.dispatchers;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.rimanware.volcanoisland.common.LoggingReceiveActor;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerCommand;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class RequestHandlerDispatcherActor<Request> extends LoggingReceiveActor {
  private final ActorRef database;
  private final BiFunction<Request, ActorRef, Props> requestHandlerActorProps;
  private final Supplier<String> requestHandlerActorNameGenerator;
  private final Class<Request> requestType;

  private RequestHandlerDispatcherActor(
      final ActorRef database,
      final BiFunction<Request, ActorRef, Props> requestHandlerActorProps,
      final Supplier<String> requestHandlerActorNameGenerator,
      final Class<Request> requestType) {

    this.database = database;
    this.requestHandlerActorProps = requestHandlerActorProps;
    this.requestHandlerActorNameGenerator = requestHandlerActorNameGenerator;
    this.requestType = requestType;
  }

  public static <Request> RequestHandlerDispatcherActor<Request> create(
      final ActorRef database,
      final BiFunction<Request, ActorRef, Props> requestHandlerActorProps,
      final Supplier<String> requestHandlerActorNameGenerator,
      final Class<Request> requestType) {
    return new RequestHandlerDispatcherActor<>(
        database, requestHandlerActorProps, requestHandlerActorNameGenerator, requestType);
  }

  public static <Request> Props props(
      final ActorRef database,
      final BiFunction<Request, ActorRef, Props> requestHandlerActorProps,
      final Supplier<String> requestHandlerActorNameGenerator,
      final Class<Request> requestType) {
    return Props.create(
        RequestHandlerDispatcherActor.class,
        () ->
            RequestHandlerDispatcherActor.create(
                database, requestHandlerActorProps, requestHandlerActorNameGenerator, requestType));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            requestType,
            request -> {
              final ActorRef newRequestHandlerActor =
                  getContext()
                      .actorOf(
                          requestHandlerActorProps.apply(request, database),
                          requestHandlerActorNameGenerator.get());
              newRequestHandlerActor.forward(RequestHandlerCommand.process(), getContext());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }
}
