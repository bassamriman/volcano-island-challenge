package com.rimanware.volcanoisland.services;

import akka.actor.ActorRef;
import akka.util.Timeout;
import com.rimanware.volcanoisland.services.api.BookingService;
import com.rimanware.volcanoisland.services.models.requests.BookingRequest;
import com.rimanware.volcanoisland.services.models.requests.UpdateBookingRequest;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.util.concurrent.CompletionStage;

import static akka.pattern.PatternsCS.ask;

public final class BookingServiceImpl implements BookingService {
  private final ActorRef createBookingRequestHandlerDispatcherActor;
  private final ActorRef updateBookingRequestHandlerDispatcherActor;
  private final ActorRef deleteBookingRequestHandlerDispatcherActor;
  private final Timeout timeout;

  private BookingServiceImpl(
      final ActorRef createBookingRequestHandlerDispatcherActor,
      final ActorRef updateBookingRequestHandlerDispatcherActor,
      final ActorRef deleteBookingRequestHandlerDispatcherActor,
      final Timeout timeout) {
    this.createBookingRequestHandlerDispatcherActor = createBookingRequestHandlerDispatcherActor;
    this.updateBookingRequestHandlerDispatcherActor = updateBookingRequestHandlerDispatcherActor;
    this.deleteBookingRequestHandlerDispatcherActor = deleteBookingRequestHandlerDispatcherActor;
    this.timeout = timeout;
  }

  public static BookingServiceImpl create(
      final ActorRef createBookingRequestHandlerDispatcherActor,
      final ActorRef updateBookingRequestHandlerDispatcherActor,
      final ActorRef deleteBookingRequestHandlerDispatcherActor,
      final Timeout timeout) {
    return new BookingServiceImpl(
        createBookingRequestHandlerDispatcherActor,
        updateBookingRequestHandlerDispatcherActor,
        deleteBookingRequestHandlerDispatcherActor,
        timeout);
  }

  @Override
  public CompletionStage<RequestHandlerResponse> createBooking(
      final BookingRequest bookingRequest) {
    return ask(createBookingRequestHandlerDispatcherActor, bookingRequest, timeout)
        .thenApply((RequestHandlerResponse.class::cast));
  }

  @Override
  public CompletionStage<RequestHandlerResponse> updateBooking(
      final UpdateBookingRequest updateBookingRequest) {
    return ask(updateBookingRequestHandlerDispatcherActor, updateBookingRequest, timeout)
        .thenApply((RequestHandlerResponse.class::cast));
  }

  @Override
  public CompletionStage<RequestHandlerResponse> deleteBooking(final String id) {
    return ask(deleteBookingRequestHandlerDispatcherActor, id, timeout)
        .thenApply((RequestHandlerResponse.class::cast));
  }
}
