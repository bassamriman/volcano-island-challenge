package com.rimanware.volcanoisland.services.requesthandlers;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.rimanware.volcanoisland.common.UtilityFunctions;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseResponse;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseResponse;
import com.rimanware.volcanoisland.database.models.Booking;
import com.rimanware.volcanoisland.errors.api.APIErrorMessages;
import com.rimanware.volcanoisland.services.models.requests.BookingRequest;
import com.rimanware.volcanoisland.services.models.responses.BookingConfirmation;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerCommand;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;
import com.rimanware.volcanoisland.services.requesthandlers.common.BookingRequestState;
import com.rimanware.volcanoisland.services.requesthandlers.common.RequestHandlerActor;
import com.rimanware.volcanoisland.services.requesthandlers.common.ResponseCollector;

import java.time.LocalDate;

import static com.rimanware.volcanoisland.services.requesthandlers.common.RequestHandlerHelper.collectAllFailures;

public final class CreateBookingRequestHandlerActor
    extends RequestHandlerActor<BookingRequestState> {
  private final APIErrorMessages apiErrorMessages;
  private final BookingRequest bookingRequest;
  private final ActorRef database;

  private CreateBookingRequestHandlerActor(
      final BookingRequest bookingRequest,
      final APIErrorMessages apiErrorMessages,
      final ActorRef database) {
    this.apiErrorMessages = apiErrorMessages;
    this.bookingRequest = bookingRequest;
    this.database = database;
  }

  private static CreateBookingRequestHandlerActor create(
      final BookingRequest bookingRequest,
      final APIErrorMessages apiErrorMessages,
      final ActorRef database) {
    return new CreateBookingRequestHandlerActor(bookingRequest, apiErrorMessages, database);
  }

  public static Props props(
      final BookingRequest bookingRequest,
      final APIErrorMessages apiErrorMessages,
      final ActorRef database) {
    return Props.create(
        CreateBookingRequestHandlerActor.class,
        () -> CreateBookingRequestHandlerActor.create(bookingRequest, apiErrorMessages, database));
  }

  private static ImmutableList<LocalDate> datesToRollBack(
      final BookingRequestState bookingRequestState) {
    return bookingRequestState.getNewlyBookedDates();
  }

  @Override
  public AbstractActor.Receive createReceive() {
    return inactive();
  }

  private AbstractActor.Receive inactive() {
    return receiveBuilder()
        .match(
            RequestHandlerCommand.Process.class,
            book -> {
              final ActorRef sender = sender();
              final ImmutableSet<LocalDate> daysToBook =
                  UtilityFunctions.generateAllDatesInRange(
                      bookingRequest.getArrivalDate(), bookingRequest.getDepartureDate());

              final Booking booking = Booking.fromBookingRequest(bookingRequest);
              daysToBook.forEach(
                  day -> database.tell(SingleDateDatabaseCommand.book(booking, day), self()));

              final ImmutableSet<String> expectedDateCreateResponses =
                  daysToBook.stream()
                      .map(LocalDate::toString)
                      .collect(ImmutableSet.toImmutableSet());

              getContext()
                  .become(
                      collectingResponses(
                          ResponseCollector.empty(expectedDateCreateResponses),
                          BookingRequestState.empty(booking, sender)));
            })
        .matchAny(o -> log.info("received unknown message"))
        .build();
  }

  @Override
  protected Receive collectingResponses(
      final ResponseCollector<String> currentResponseCollector,
      final BookingRequestState currentCreateBookingRequestState) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseResponse.ProbatoryBookingConfirmation.class,
            bookingConfirmation -> {
              final LocalDate bookedDate = bookingConfirmation.getBookingConfirmation().getDate();
              final String bookedDateAsString = bookedDate.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(bookedDateAsString);
              final BookingRequestState newCreateBookingRequestState =
                  currentCreateBookingRequestState.addNewlyBookedDate(bookedDate);

              nextStateOrCompleteRequestWithRollback(
                  newResponseCollector,
                  newCreateBookingRequestState,
                  CreateBookingRequestHandlerActor::datesToRollBack,
                  database);
            })
        .match(
            SingleDateDatabaseResponse.IsBooked.class,
            isBooked -> {
              final LocalDate isBookedDate = isBooked.getDate();
              final String isBookedAsString = isBookedDate.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(isBookedAsString);
              final BookingRequestState newCreateBookingRequestState =
                  currentCreateBookingRequestState.addAlreadyBookedDate(isBookedDate);

              nextStateOrCompleteRequestWithRollback(
                  newResponseCollector,
                  newCreateBookingRequestState,
                  CreateBookingRequestHandlerActor::datesToRollBack,
                  database);
            })
        .match(
            RollingMonthDatabaseResponse.RequestedDateOutOfRange.class,
            requestedDateOutOfRange -> {
              final LocalDate dateOutOfRange = requestedDateOutOfRange.getRequestedDate();
              final String dateOutOfRangeAsString = dateOutOfRange.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(dateOutOfRangeAsString);
              final BookingRequestState newCreateBookingRequestState =
                  currentCreateBookingRequestState.addOutOfRangeDate(requestedDateOutOfRange);

              nextStateOrCompleteRequestWithRollback(
                  newResponseCollector,
                  newCreateBookingRequestState,
                  CreateBookingRequestHandlerActor::datesToRollBack,
                  database);
            })
        .matchAny(o -> log.info("received unknown message"))
        .build();
  }

  @Override
  protected RequestHandlerResponse createResponse(
      final BookingRequestState createBookingRequestState) {
    if (!createBookingRequestState.getAlreadyBookedDates().isEmpty()
        || !createBookingRequestState.getOutOfRangeDates().isEmpty()) {
      return collectAllFailures(
          createBookingRequestState.getAlreadyBookedDates(),
          createBookingRequestState.getOutOfRangeDates(),
          apiErrorMessages);

    } else {
      return RequestHandlerResponse.Success.succeeded(
          BookingConfirmation.create(createBookingRequestState.getBooking().getId()));
    }
  }
}
