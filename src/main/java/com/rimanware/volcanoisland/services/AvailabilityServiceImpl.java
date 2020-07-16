package com.rimanware.volcanoisland.services;

import akka.actor.ActorRef;
import akka.util.Timeout;
import com.rimanware.volcanoisland.services.api.AvailabilityService;
import com.rimanware.volcanoisland.services.models.requests.AvailabilitiesRequest;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.util.concurrent.CompletionStage;

import static akka.pattern.PatternsCS.ask;

public final class AvailabilityServiceImpl implements AvailabilityService {
  private final ActorRef availabilityRequestHandlerDispatcherActor;
  private final Timeout timeout;

  private AvailabilityServiceImpl(
      final ActorRef availabilityRequestHandlerDispatcherActor, final Timeout timeout) {
    this.availabilityRequestHandlerDispatcherActor = availabilityRequestHandlerDispatcherActor;
    this.timeout = timeout;
  }

  public static AvailabilityServiceImpl create(
      final ActorRef availabilityRequestHandlerDispatcherActor, final Timeout timeout) {
    return new AvailabilityServiceImpl(availabilityRequestHandlerDispatcherActor, timeout);
  }

  @Override
  public CompletionStage<RequestHandlerResponse> getAvailabilities(
      final AvailabilitiesRequest.DateRange availabilitiesRequest) {

    return ask(availabilityRequestHandlerDispatcherActor, availabilitiesRequest, timeout)
        .thenApply((RequestHandlerResponse.class::cast));
  }

  @Override
  public CompletionStage<RequestHandlerResponse> getAvailabilities() {
    return ask(availabilityRequestHandlerDispatcherActor, AvailabilitiesRequest.empty(), timeout)
        .thenApply((RequestHandlerResponse.class::cast));
  }
}
