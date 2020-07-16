package com.rimanware.volcanoisland.services.requesthandlers.dispatchers;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.rimanware.volcanoisland.errors.api.APIErrorMessages;
import com.rimanware.volcanoisland.services.models.requests.AvailabilitiesRequest;
import com.rimanware.volcanoisland.services.models.requests.BookingRequest;
import com.rimanware.volcanoisland.services.models.requests.UpdateBookingRequest;
import com.rimanware.volcanoisland.services.requesthandlers.AvailabilityRequestHandlerActor;
import com.rimanware.volcanoisland.services.requesthandlers.CreateBookingRequestHandlerActor;
import com.rimanware.volcanoisland.services.requesthandlers.DeleteBookingRequestHandlerActor;
import com.rimanware.volcanoisland.services.requesthandlers.UpdateBookingRequestHandlerActor;

import java.util.UUID;

public final class RequestHandlerDispatcherActorFactory {

  public static final String AVAILABILITY_REQUEST_HANDLER_ACTOR =
      "AvailabilityRequestHandlerActor-";
  public static final String CREATE_BOOKING_REQUEST_HANDLER_ACTOR =
      "CreateBookingRequestHandlerActor-";
  public static final String UPDATE_BOOKING_REQUEST_HANDLER_ACTOR =
      "UpdateBookingRequestHandlerActor";
  public static final String DELETE_BOOKING_REQUEST_HANDLER_ACTOR =
      "DeleteBookingRequestHandlerActor-";

  public static Props availabilityRequestHandlerDispatcherActorProps(
      final ActorRef database, final APIErrorMessages apiErrorMessages) {
    return RequestHandlerDispatcherActor.props(
        database,
        apiErrorMessages,
        AvailabilityRequestHandlerActor::props,
        () -> AVAILABILITY_REQUEST_HANDLER_ACTOR + UUID.randomUUID().toString(),
        AvailabilitiesRequest.class);
  }

  public static Props createBookingRequestHandlerDispatcherActorProps(
      final ActorRef database, final APIErrorMessages apiErrorMessages) {
    return RequestHandlerDispatcherActor.props(
        database,
        apiErrorMessages,
        CreateBookingRequestHandlerActor::props,
        () -> CREATE_BOOKING_REQUEST_HANDLER_ACTOR + UUID.randomUUID().toString(),
        BookingRequest.class);
  }

  public static Props updateBookingRequestHandlerDispatcherActorProps(
      final ActorRef database, final APIErrorMessages apiErrorMessages) {
    return RequestHandlerDispatcherActor.props(
        database,
        apiErrorMessages,
        UpdateBookingRequestHandlerActor::props,
        () -> UPDATE_BOOKING_REQUEST_HANDLER_ACTOR + "-" + UUID.randomUUID().toString(),
        UpdateBookingRequest.class);
  }

  public static Props deleteBookingRequestHandlerDispatcherActorProps(
      final ActorRef database, final APIErrorMessages apiErrorMessages) {
    return RequestHandlerDispatcherActor.props(
        database,
        apiErrorMessages,
        DeleteBookingRequestHandlerActor::props,
        () -> DELETE_BOOKING_REQUEST_HANDLER_ACTOR + UUID.randomUUID().toString(),
        String.class);
  }
}
