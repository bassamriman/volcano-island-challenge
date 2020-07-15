package com.rimanware.volcanoisland.services.requesthandlers.dispatchers;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.rimanware.volcanoisland.errors.APIErrorMessages;
import com.rimanware.volcanoisland.services.models.requests.AvailabilitiesRequest;
import com.rimanware.volcanoisland.services.models.requests.BookingRequest;
import com.rimanware.volcanoisland.services.models.requests.UpdateBookingRequest;
import com.rimanware.volcanoisland.services.requesthandlers.AvailabilityRequestHandlerActor;
import com.rimanware.volcanoisland.services.requesthandlers.CreateBookingRequestHandlerActor;
import com.rimanware.volcanoisland.services.requesthandlers.DeleteBookingRequestHandlerActor;
import com.rimanware.volcanoisland.services.requesthandlers.UpdateBookingRequestHandlerActor;

import java.util.UUID;

public final class RequestHandlerDispatcherActorFactory {

  public static Props availabilityRequestHandlerDispatcherActorProps(
      final ActorRef database, final APIErrorMessages apiErrorMessages) {
    return RequestHandlerDispatcherActor.props(
        database,
        apiErrorMessages,
        AvailabilityRequestHandlerActor::props,
        () -> "AvailabilityRequestHandlerActor-" + UUID.randomUUID().toString(),
        AvailabilitiesRequest.class);
  }

  public static Props createBookingRequestHandlerDispatcherActorProps(
      final ActorRef database, final APIErrorMessages apiErrorMessages) {
    return RequestHandlerDispatcherActor.props(
        database,
        apiErrorMessages,
        CreateBookingRequestHandlerActor::props,
        () -> "CreateBookingRequestHandlerActor-" + UUID.randomUUID().toString(),
        BookingRequest.class);
  }

  public static Props updateBookingRequestHandlerDispatcherActorProps(
      final ActorRef database, final APIErrorMessages apiErrorMessages) {
    return RequestHandlerDispatcherActor.props(
        database,
        apiErrorMessages,
        UpdateBookingRequestHandlerActor::props,
        () -> "UpdateBookingRequestHandlerActor-" + UUID.randomUUID().toString(),
        UpdateBookingRequest.class);
  }

  public static Props deleteBookingRequestHandlerDispatcherActorProps(
      final ActorRef database, final APIErrorMessages apiErrorMessages) {
    return RequestHandlerDispatcherActor.props(
        database,
        apiErrorMessages,
        DeleteBookingRequestHandlerActor::props,
        () -> "DeleteBookingRequestHandlerActor-" + UUID.randomUUID().toString(),
        String.class);
  }
}
