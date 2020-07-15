package com.rimanware.volcanoisland.services.requesthandlers;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.rimanware.volcanoisland.common.LoggingReceiveActor;
import com.rimanware.volcanoisland.common.UtilityFunctions;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseResponse;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseResponse;
import com.rimanware.volcanoisland.database.models.Booking;
import com.rimanware.volcanoisland.services.models.requests.BookingRequest;
import com.rimanware.volcanoisland.services.models.responses.BookingConfirmation;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerCommand;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static com.rimanware.volcanoisland.services.requesthandlers.common.RequestHandlerHelper.collectAllFailures;

public final class CreateBookingRequestHandlerActor extends LoggingReceiveActor {
  private final BookingRequest bookingRequest;
  private final ActorRef database;

  private CreateBookingRequestHandlerActor(
      final BookingRequest bookingRequest, final ActorRef database) {
    this.bookingRequest = bookingRequest;
    this.database = database;
  }

  private static CreateBookingRequestHandlerActor create(
      final BookingRequest bookingRequest, final ActorRef database) {
    return new CreateBookingRequestHandlerActor(bookingRequest, database);
  }

  public static Props props(final BookingRequest bookingRequest, final ActorRef database) {
    return Props.create(
        CreateBookingRequestHandlerActor.class,
        () -> CreateBookingRequestHandlerActor.create(bookingRequest, database));
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
                  Stream.iterate(bookingRequest.getArrivalDate(), d -> d.plusDays(1))
                      .limit(
                          ChronoUnit.DAYS.between(
                              bookingRequest.getArrivalDate(),
                              // Increment by one because ChronoUnit.DAYS.between API
                              // to date is exclusive
                              bookingRequest.getDepartureDate().plusDays(1)))
                      .collect(ImmutableSet.toImmutableSet());
              final Booking booking = Booking.fromBookingRequest(bookingRequest);
              daysToBook.forEach(
                  day -> database.tell(SingleDateDatabaseCommand.book(booking, day), self()));

              getContext()
                  .become(
                      collectingResponses(
                          ImmutableSet.of(),
                          ImmutableList.of(),
                          ImmutableList.of(),
                          ImmutableList.of(),
                          daysToBook.stream()
                              .map(LocalDate::toString)
                              .collect(ImmutableSet.toImmutableSet()),
                          booking,
                          sender));
            })
        .matchAny(o -> log.info("received unknown message"))
        .build();
  }

  // TODO: clean up duplicated code
  private AbstractActor.Receive collectingResponses(
      final ImmutableSet<String> collectedDateBookingResponses,
      final ImmutableList<LocalDate> bookedDates,
      final ImmutableList<LocalDate> alreadyBookedFailedDates,
      final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange>
          outOfRangeFailedDates,
      final ImmutableSet<String> expectedDateBookingResponses,
      final Booking booking,
      final ActorRef sender) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseResponse.ProbatoryBookingConfirmation.class,
            bookingConfirmation -> {
              final LocalDate bookedDate = bookingConfirmation.getBookingConfirmation().getDate();
              final String bookedDateAsString = bookedDate.toString();

              if (expectedDateBookingResponses.contains(bookedDateAsString)) {
                final ImmutableList<LocalDate> newBookedDates =
                    UtilityFunctions.addToImmutableList(bookedDates, bookedDate);

                final ImmutableSet<String> newCollectedDateBookingResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateBookingResponses, bookedDateAsString);

                if (newCollectedDateBookingResponses.containsAll(expectedDateBookingResponses)) {
                  handleResult(
                      newBookedDates,
                      alreadyBookedFailedDates,
                      outOfRangeFailedDates,
                      booking,
                      sender);
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateBookingResponses,
                              newBookedDates,
                              alreadyBookedFailedDates,
                              outOfRangeFailedDates,
                              expectedDateBookingResponses,
                              booking,
                              sender));
                }
              } else {
                throw new IllegalStateException(
                    "Received response for a date that wasn't expected");
              }
            })
        .match(
            SingleDateDatabaseResponse.IsBooked.class,
            isBooked -> {
              final LocalDate bookedDate = isBooked.getDate();
              final String bookedDateAsString = bookedDate.toString();

              if (expectedDateBookingResponses.contains(bookedDateAsString)) {
                final ImmutableList<LocalDate> newAlreadyBookedFailedDates =
                    UtilityFunctions.addToImmutableList(alreadyBookedFailedDates, bookedDate);

                final ImmutableSet<String> newCollectedDateBookingResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateBookingResponses, bookedDateAsString);

                if (newCollectedDateBookingResponses.containsAll(expectedDateBookingResponses)) {
                  handleResult(
                      bookedDates,
                      newAlreadyBookedFailedDates,
                      outOfRangeFailedDates,
                      booking,
                      sender);
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateBookingResponses,
                              bookedDates,
                              newAlreadyBookedFailedDates,
                              outOfRangeFailedDates,
                              expectedDateBookingResponses,
                              booking,
                              sender));
                }

              } else {
                throw new IllegalStateException(
                    "Received response for a date that wasn't expected");
              }
            })
        .match(
            RollingMonthDatabaseResponse.RequestedDateOutOfRange.class,
            requestedDateOutOfRange -> {
              final LocalDate dateOutOfRange = requestedDateOutOfRange.getRequestedDate();
              final String dateOutOfRangeAsString = dateOutOfRange.toString();

              if (expectedDateBookingResponses.contains(dateOutOfRangeAsString)) {
                final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange>
                    newOutOfRangeFailedDates =
                        UtilityFunctions.addToImmutableList(
                            outOfRangeFailedDates, requestedDateOutOfRange);

                final ImmutableSet<String> newCollectedDateBookingResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateBookingResponses, dateOutOfRangeAsString);

                if (newCollectedDateBookingResponses.containsAll(expectedDateBookingResponses)) {
                  handleResult(
                      bookedDates,
                      alreadyBookedFailedDates,
                      newOutOfRangeFailedDates,
                      booking,
                      sender);
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateBookingResponses,
                              bookedDates,
                              alreadyBookedFailedDates,
                              newOutOfRangeFailedDates,
                              expectedDateBookingResponses,
                              booking,
                              sender));
                }

              } else {
                throw new IllegalStateException(
                    "Received response for a date that wasn't expected");
              }
            })
        .matchAny(o -> log.info("received unknown message"))
        .build();
  }

  private void handleResult(
      final ImmutableList<LocalDate> bookedDates,
      final ImmutableList<LocalDate> alreadyBookedFailedDates,
      final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange>
          outOfRangeFailedDates,
      final Booking booking,
      final ActorRef sender) {
    if (!alreadyBookedFailedDates.isEmpty() || !outOfRangeFailedDates.isEmpty()) {
      // Collect All errors
      final RequestHandlerResponse.Failure multipleDateFailures =
          collectAllFailures(alreadyBookedFailedDates, outOfRangeFailedDates);

      // Inform sender of failure
      sender.tell(multipleDateFailures, self());

      // Rollback updated dates
      bookedDates.forEach(date -> database.tell(SingleDateDatabaseCommand.revert(date), self()));
    } else {

      sender.tell(
          RequestHandlerResponse.Success.succeeded(BookingConfirmation.create(booking.getId())),
          self());

      // Commit all changes
      bookedDates.forEach(date -> database.tell(SingleDateDatabaseCommand.commit(date), self()));
    }
    // We are done handling the request this actor will suicide
    self().tell(PoisonPill.getInstance(), self());
  }
}
