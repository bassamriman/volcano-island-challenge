package com.rimanware.volcanoisland.services.requesthandlers;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.rimanware.volcanoisland.common.UtilityFunctions;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseCommand;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseResponse;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseResponse;
import com.rimanware.volcanoisland.database.models.Booking;
import com.rimanware.volcanoisland.errors.APIErrorImpl;
import com.rimanware.volcanoisland.errors.api.APIErrorMessages;
import com.rimanware.volcanoisland.services.models.requests.UpdateBookingRequest;
import com.rimanware.volcanoisland.services.models.responses.BookingConfirmation;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerCommand;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;
import com.rimanware.volcanoisland.services.requesthandlers.common.BookingRequestState;
import com.rimanware.volcanoisland.services.requesthandlers.common.RequestHandlerActor;
import com.rimanware.volcanoisland.services.requesthandlers.common.RequestHandlerHelper;
import com.rimanware.volcanoisland.services.requesthandlers.common.ResponseCollector;

import java.time.LocalDate;

public final class UpdateBookingRequestHandlerActor
    extends RequestHandlerActor<BookingRequestState> {
  private final APIErrorMessages apiErrorMessages;
  private final UpdateBookingRequest updateRequest;
  private final ActorRef database;

  private UpdateBookingRequestHandlerActor(
      final UpdateBookingRequest updateRequest,
      final APIErrorMessages apiErrorMessages,
      final ActorRef database) {
    this.apiErrorMessages = apiErrorMessages;
    this.updateRequest = updateRequest;
    this.database = database;
  }

  private static UpdateBookingRequestHandlerActor create(
      final UpdateBookingRequest updateBookingRequest,
      final APIErrorMessages apiErrorMessages,
      final ActorRef database) {
    return new UpdateBookingRequestHandlerActor(updateBookingRequest, apiErrorMessages, database);
  }

  public static Props props(
      final UpdateBookingRequest updateBookingRequest,
      final APIErrorMessages apiErrorMessages,
      final ActorRef database) {
    return Props.create(
        UpdateBookingRequestHandlerActor.class,
        () ->
            UpdateBookingRequestHandlerActor.create(
                updateBookingRequest, apiErrorMessages, database));
  }

  private static ImmutableList<LocalDate> datesToRollBack(
      final BookingRequestState bookingRequestState) {
    return bookingRequestState.getNewlyBookedDates();
  }

  @Override
  public Receive createReceive() {
    return inactive();
  }

  private Receive inactive() {
    return receiveBuilder()
        .match(
            RequestHandlerCommand.Process.class,
            book -> {
              final ActorRef sender = sender();
              database.tell(RollingMonthDatabaseCommand.getQueryableDates(), self());
              getContext().become(waitingForQueryableDates(sender));
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  private Receive waitingForQueryableDates(final ActorRef originalSender) {
    return receiveBuilder()
        .match(
            RollingMonthDatabaseResponse.QueryableDates.class,
            queryableDates -> {
              final Booking updatedBooking = Booking.fromUpdateRequest(updateRequest);
              database.tell(SingleDateDatabaseCommand.update(updatedBooking), self());

              final ImmutableSet<String> daysToBookAsString =
                  UtilityFunctions.generateAllDatesInRange(
                          updatedBooking.getArrivalDate(), updatedBooking.getDepartureDate())
                      .stream()
                      .map(LocalDate::toString)
                      .collect(ImmutableSet.toImmutableSet());

              final ImmutableSet<String> expectedDateUpdateResponses =
                  UtilityFunctions.combine(
                      queryableDates.getQueryableDates().stream()
                          .map(LocalDate::toString)
                          .collect(ImmutableSet.toImmutableSet()),
                      daysToBookAsString);

              getContext()
                  .become(
                      collectingResponses(
                          ResponseCollector.empty(expectedDateUpdateResponses),
                          BookingRequestState.empty(updatedBooking, originalSender)));
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  @Override
  protected Receive collectingResponses(
      final ResponseCollector<String> currentResponseCollector,
      final BookingRequestState currentUpdateBookingRequestState) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseResponse.ProbatoryUpdateConfirmation.class,
            probatoryUpdateConfirmation -> {
              final LocalDate updatedDate = probatoryUpdateConfirmation.getDate();
              final String updatedDateAsString = updatedDate.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(updatedDateAsString);
              final BookingRequestState newUpdateBookingRequestState =
                  currentUpdateBookingRequestState
                      .addNewlyBookedDate(updatedDate)
                      .foundBookingToBeUpdated(
                          probatoryUpdateConfirmation.getOverridesPreviousUpdate());

              nextStateOrCompleteRequestWithRollback(
                  newResponseCollector,
                  newUpdateBookingRequestState,
                  UpdateBookingRequestHandlerActor::datesToRollBack,
                  database);
            })
        .match(
            SingleDateDatabaseResponse.IsBooked.class,
            isBooked -> {
              final LocalDate isBookedDate = isBooked.getDate();
              final String isBookedAsString = isBookedDate.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(isBookedAsString);
              final BookingRequestState newUpdateBookingRequestState =
                  currentUpdateBookingRequestState.addAlreadyBookedDate(isBookedDate);

              nextStateOrCompleteRequestWithRollback(
                  newResponseCollector,
                  newUpdateBookingRequestState,
                  UpdateBookingRequestHandlerActor::datesToRollBack,
                  database);
            })
        .match(
            RollingMonthDatabaseResponse.RequestedDatesOutOfRange.class,
            requestedDatesOutOfRange -> {
              final ImmutableList<LocalDate> outOfRangeDates =
                  requestedDatesOutOfRange.getRequestedDatesOutOfRange().stream()
                      .map(RollingMonthDatabaseResponse.RequestedDateOutOfRange::getRequestedDate)
                      .collect(ImmutableList.toImmutableList());

              final ImmutableSet<String> outOfRangeDatesAsString =
                  outOfRangeDates.stream()
                      .map(LocalDate::toString)
                      .collect(ImmutableSet.toImmutableSet());

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(outOfRangeDatesAsString);
              final BookingRequestState newUpdateBookingRequestState =
                  currentUpdateBookingRequestState.addOutOfRangeDates(
                      requestedDatesOutOfRange.getRequestedDatesOutOfRange());

              nextStateOrCompleteRequestWithRollback(
                  newResponseCollector,
                  newUpdateBookingRequestState,
                  UpdateBookingRequestHandlerActor::datesToRollBack,
                  database);
            })
        .match(
            SingleDateDatabaseResponse.DoesntQualifyForUpdateConfirmation.class,
            doesntQualifyForUpdateConfirmation -> {
              final LocalDate notQualifyingUpdateConfirmationDate =
                  doesntQualifyForUpdateConfirmation.getDate();
              final String notQualifyingUpdateConfirmationDateAsString =
                  notQualifyingUpdateConfirmationDate.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(notQualifyingUpdateConfirmationDateAsString);

              nextStateOrCompleteRequest(newResponseCollector, currentUpdateBookingRequestState);
              nextStateOrCompleteRequestWithRollback(
                  newResponseCollector,
                  currentUpdateBookingRequestState,
                  UpdateBookingRequestHandlerActor::datesToRollBack,
                  database);
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  @Override
  protected RequestHandlerResponse createResponse(
      final BookingRequestState updateBookingRequestState) {

    if (!updateBookingRequestState.getFoundBookingToBeUpdated()) {

      return RequestHandlerResponse.Failure.failed(
          APIErrorImpl.BookingIdNotFoundError, apiErrorMessages);

    } else if (!updateBookingRequestState.getAlreadyBookedDates().isEmpty()
        || !updateBookingRequestState.getOutOfRangeDates().isEmpty()) {

      return RequestHandlerHelper.collectAllFailures(
          updateBookingRequestState.getAlreadyBookedDates(),
          updateBookingRequestState.getOutOfRangeDates(),
          apiErrorMessages);

    } else {
      return RequestHandlerResponse.Success.succeeded(
          BookingConfirmation.create(updateBookingRequestState.getBooking().getId()));
    }
  }
}
