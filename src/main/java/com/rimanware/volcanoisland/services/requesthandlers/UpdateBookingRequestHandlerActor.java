package com.rimanware.volcanoisland.services.requesthandlers;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.rimanware.volcanoisland.common.LoggingReceiveActor;
import com.rimanware.volcanoisland.common.UtilityFunctions;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseCommand;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseResponse;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseResponse;
import com.rimanware.volcanoisland.database.models.Booking;
import com.rimanware.volcanoisland.errors.APIError;
import com.rimanware.volcanoisland.errors.APIErrorMessages;
import com.rimanware.volcanoisland.services.models.requests.UpdateBookingRequest;
import com.rimanware.volcanoisland.services.models.responses.BookingConfirmation;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerCommand;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;
import com.rimanware.volcanoisland.services.requesthandlers.common.RequestHandlerHelper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

public final class UpdateBookingRequestHandlerActor extends LoggingReceiveActor {
  private final UpdateBookingRequest updateRequest;
  private final ActorRef database;

  private UpdateBookingRequestHandlerActor(
      final UpdateBookingRequest updateBookingRequest, final ActorRef database) {
    this.updateRequest = updateBookingRequest;
    this.database = database;
  }

  private static UpdateBookingRequestHandlerActor create(
      final UpdateBookingRequest updateBookingRequest, final ActorRef database) {
    return new UpdateBookingRequestHandlerActor(updateBookingRequest, database);
  }

  public static Props props(
      final UpdateBookingRequest updateBookingRequest, final ActorRef database) {
    return Props.create(
        UpdateBookingRequestHandlerActor.class,
        () -> UpdateBookingRequestHandlerActor.create(updateBookingRequest, database));
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
              getContext().become(waitingForNumberOfQueryableDates(sender));
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  private Receive waitingForNumberOfQueryableDates(final ActorRef originalSender) {
    return receiveBuilder()
        .match(
            RollingMonthDatabaseResponse.QueryableDates.class,
            queryableDates -> {
              final Booking updatedBooking = Booking.fromUpdateRequest(updateRequest);
              database.tell(SingleDateDatabaseCommand.update(updatedBooking), self());

              final ImmutableSet<String> daysToBookAsString =
                  Stream.iterate(updatedBooking.getArrivalDate(), d -> d.plusDays(1))
                      .limit(
                          ChronoUnit.DAYS.between(
                              updatedBooking.getArrivalDate(),
                              // Increment by one because ChronoUnit.DAYS.between API
                              // to date is exclusive
                              updatedBooking.getDepartureDate().plusDays(1)))
                      .map(LocalDate::toString)
                      .collect(ImmutableSet.toImmutableSet());

              final ImmutableSet<String> expectedDateUpdateResponses =
                  UtilityFunctions.combine(queryableDates.getQueryableDates(), daysToBookAsString);

              getContext()
                  .become(
                      collectingResponses(
                          ImmutableSet.of(),
                          ImmutableList.of(),
                          ImmutableList.of(),
                          ImmutableList.of(),
                          false,
                          expectedDateUpdateResponses,
                          updatedBooking,
                          originalSender));
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  // TODO: clean up duplicated code
  private Receive collectingResponses(
      final ImmutableSet<String> collectedDateUpdateResponses,
      final ImmutableList<LocalDate> updatedDates,
      final ImmutableList<LocalDate> alreadyBookedFailedDates,
      final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange>
          outOfRangeFailedDates,
      final Boolean foundBookingToBeUpdated,
      final ImmutableSet<String> expectedDateUpdateResponses,
      final Booking booking,
      final ActorRef originalSender) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseResponse.ProbatoryUpdateConfirmation.class,
            probatoryUpdateConfirmation -> {
              final LocalDate updatedDate = probatoryUpdateConfirmation.getDate();
              final String updatedDateAsString = updatedDate.toString();

              if (expectedDateUpdateResponses.contains(updatedDateAsString)) {
                final ImmutableList<LocalDate> newUpdatedDates =
                    UtilityFunctions.addToImmutableList(updatedDates, updatedDate);

                final Boolean newFoundBookingToBeUpdated =
                    foundBookingToBeUpdated
                        || probatoryUpdateConfirmation.getOverridesPreviousUpdate();

                final ImmutableSet<String> newCollectedDateUpdateResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateUpdateResponses, updatedDateAsString);

                if (newCollectedDateUpdateResponses.containsAll(expectedDateUpdateResponses)) {
                  handleResult(
                      newUpdatedDates,
                      alreadyBookedFailedDates,
                      outOfRangeFailedDates,
                      newFoundBookingToBeUpdated,
                      booking,
                      originalSender);
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateUpdateResponses,
                              newUpdatedDates,
                              alreadyBookedFailedDates,
                              outOfRangeFailedDates,
                              newFoundBookingToBeUpdated,
                              expectedDateUpdateResponses,
                              booking,
                              originalSender));
                }
              } else {
                throw new IllegalStateException(
                    "Received response for a date that wasn't expected");
              }
            })
        .match(
            SingleDateDatabaseResponse.IsBooked.class,
            isBooked -> {
              final LocalDate isBookedDate = isBooked.getDate();
              final String isBookedAsString = isBookedDate.toString();

              if (expectedDateUpdateResponses.contains(isBookedAsString)) {
                final ImmutableList<LocalDate> newAlreadyBookedFailedDates =
                    UtilityFunctions.addToImmutableList(alreadyBookedFailedDates, isBookedDate);

                final ImmutableSet<String> newCollectedDateUpdateResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateUpdateResponses, isBookedAsString);

                if (newCollectedDateUpdateResponses.containsAll(expectedDateUpdateResponses)) {
                  handleResult(
                      updatedDates,
                      newAlreadyBookedFailedDates,
                      outOfRangeFailedDates,
                      foundBookingToBeUpdated,
                      booking,
                      originalSender);
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateUpdateResponses,
                              updatedDates,
                              newAlreadyBookedFailedDates,
                              outOfRangeFailedDates,
                              foundBookingToBeUpdated,
                              expectedDateUpdateResponses,
                              booking,
                              originalSender));
                }
              } else {
                throw new IllegalStateException(
                    "Received response for a date that wasn't expected");
              }
            })
        .match(
            SingleDateDatabaseResponse.DoesntQualifyForUpdateConfirmation.class,
            doesntQualifyForUpdateConfirmation -> {
              final LocalDate notQualifyingUpdateConfirmationDate =
                  doesntQualifyForUpdateConfirmation.getDate();
              final String notQualifyingUpdateConfirmationDateAsString =
                  notQualifyingUpdateConfirmationDate.toString();

              if (expectedDateUpdateResponses.contains(
                  notQualifyingUpdateConfirmationDateAsString)) {

                final ImmutableSet<String> newCollectedDateUpdateResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateUpdateResponses, notQualifyingUpdateConfirmationDateAsString);

                if (newCollectedDateUpdateResponses.containsAll(expectedDateUpdateResponses)) {
                  handleResult(
                      updatedDates,
                      alreadyBookedFailedDates,
                      outOfRangeFailedDates,
                      foundBookingToBeUpdated,
                      booking,
                      originalSender);
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateUpdateResponses,
                              updatedDates,
                              alreadyBookedFailedDates,
                              outOfRangeFailedDates,
                              foundBookingToBeUpdated,
                              expectedDateUpdateResponses,
                              booking,
                              originalSender));
                }
              } else {
                throw new IllegalStateException(
                    "Received response for a date that wasn't expected");
              }
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

              if (expectedDateUpdateResponses.containsAll(outOfRangeDatesAsString)) {

                final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange>
                    newOutOfRangeFailedDates =
                        UtilityFunctions.combine(
                            outOfRangeFailedDates,
                            requestedDatesOutOfRange.getRequestedDatesOutOfRange());
                final ImmutableSet<String> newCollectedDateUpdateResponses =
                    UtilityFunctions.combine(collectedDateUpdateResponses, outOfRangeDatesAsString);

                if (newCollectedDateUpdateResponses.containsAll(expectedDateUpdateResponses)) {
                  handleResult(
                      updatedDates,
                      alreadyBookedFailedDates,
                      newOutOfRangeFailedDates,
                      foundBookingToBeUpdated,
                      booking,
                      originalSender);
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateUpdateResponses,
                              updatedDates,
                              alreadyBookedFailedDates,
                              newOutOfRangeFailedDates,
                              foundBookingToBeUpdated,
                              expectedDateUpdateResponses,
                              booking,
                              originalSender));
                }
              } else {
                throw new IllegalStateException(
                    "Received response for a date that wasn't expected");
              }
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  private void handleResult(
      final ImmutableList<LocalDate> updatedDates,
      final ImmutableList<LocalDate> alreadyBookedFailedDates,
      final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange>
          outOfRangeFailedDates,
      final Boolean foundBookingToBeUpdated,
      final Booking booking,
      final ActorRef originalSender) {

    if (!foundBookingToBeUpdated) {

      final RequestHandlerResponse.Failure failure =
          RequestHandlerResponse.Failure.failed(
              APIError.BookingIdNotFoundError, APIErrorMessages.ENGLISH);

      // Inform sender of failure
      originalSender.tell(failure, self());

    } else if (!alreadyBookedFailedDates.isEmpty() || !outOfRangeFailedDates.isEmpty()) {

      // Rollback updated dates
      updatedDates.forEach(date -> database.tell(SingleDateDatabaseCommand.revert(date), self()));

      // Collect All errors
      final RequestHandlerResponse.Failure multipleDateFailures =
          RequestHandlerHelper.collectAllFailures(alreadyBookedFailedDates, outOfRangeFailedDates);

      // Inform sender of failure
      originalSender.tell(multipleDateFailures, self());

    } else {

      // Rollback updated dates
      updatedDates.forEach(date -> database.tell(SingleDateDatabaseCommand.commit(date), self()));

      originalSender.tell(
          RequestHandlerResponse.Success.succeeded(BookingConfirmation.create(booking.getId())),
          self());
    }

    // We are done handling the request this actor will suicide
    self().tell(PoisonPill.getInstance(), self());
  }
}
