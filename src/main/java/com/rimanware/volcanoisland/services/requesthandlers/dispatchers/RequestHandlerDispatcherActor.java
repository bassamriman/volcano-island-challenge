package com.rimanware.volcanoisland.services.requesthandlers.dispatchers;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.rimanware.volcanoisland.common.LoggingReceiveActor;
import com.rimanware.volcanoisland.common.TriFunction;
import com.rimanware.volcanoisland.errors.APIErrorMessages;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerCommand;

import java.util.function.Supplier;

public final class RequestHandlerDispatcherActor<Request> extends LoggingReceiveActor {
  private final ActorRef database;
  private final APIErrorMessages apiErrorMessages;
  private final TriFunction<Request, APIErrorMessages, ActorRef, Props> requestHandlerActorProps;
  private final Supplier<String> requestHandlerActorNameGenerator;
  private final Class<Request> requestType;

  private RequestHandlerDispatcherActor(
      ActorRef database,
      APIErrorMessages apiErrorMessages,
      TriFunction<Request, APIErrorMessages, ActorRef, Props> requestHandlerActorProps,
      Supplier<String> requestHandlerActorNameGenerator,
      Class<Request> requestType) {
    this.database = database;
    this.apiErrorMessages = apiErrorMessages;
    this.requestHandlerActorProps = requestHandlerActorProps;
    this.requestHandlerActorNameGenerator = requestHandlerActorNameGenerator;
    this.requestType = requestType;
  }

  public static <Request> Props props(
      final ActorRef database,
      final APIErrorMessages apiErrorMessages,
      final TriFunction<Request, APIErrorMessages, ActorRef, Props> requestHandlerActorProps,
      final Supplier<String> requestHandlerActorNameGenerator,
      final Class<Request> requestType) {
    return Props.create(
        RequestHandlerDispatcherActor.class,
        () ->
            RequestHandlerDispatcherActor.create(
                database,
                apiErrorMessages,
                requestHandlerActorProps,
                requestHandlerActorNameGenerator,
                requestType));
  }

  public static <Request> RequestHandlerDispatcherActor<Request> create(
      ActorRef database,
      APIErrorMessages apiErrorMessages,
      TriFunction<Request, APIErrorMessages, ActorRef, Props> requestHandlerActorProps,
      Supplier<String> requestHandlerActorNameGenerator,
      Class<Request> requestType) {
    return new RequestHandlerDispatcherActor<Request>(
        database,
        apiErrorMessages,
        requestHandlerActorProps,
        requestHandlerActorNameGenerator,
        requestType);
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
                          requestHandlerActorProps.apply(request, apiErrorMessages, database),
                          requestHandlerActorNameGenerator.get());
              newRequestHandlerActor.forward(RequestHandlerCommand.process(), getContext());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }
}
