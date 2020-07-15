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
import com.rimanware.volcanoisland.errors.APIError;
import com.rimanware.volcanoisland.errors.APIErrorMessages;
import com.rimanware.volcanoisland.services.models.responses.BookingConfirmation;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerCommand;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.time.LocalDate;

public final class DeleteBookingRequestHandlerActor extends LoggingReceiveActor {
  private final String bookingId;
  private final ActorRef database;

  private DeleteBookingRequestHandlerActor(final String bookingId, final ActorRef database) {
    this.bookingId = bookingId;
    this.database = database;
  }

  private static DeleteBookingRequestHandlerActor create(
      final String bookingId, final ActorRef database) {
    return new DeleteBookingRequestHandlerActor(bookingId, database);
  }

  public static Props props(final String bookingId, final ActorRef database) {
    return Props.create(
        DeleteBookingRequestHandlerActor.class,
        () -> DeleteBookingRequestHandlerActor.create(bookingId, database));
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
              database.tell(SingleDateDatabaseCommand.cancel(bookingId), self());

              getContext()
                  .become(
                      collectingResponses(
                          ImmutableSet.of(),
                          ImmutableList.of(),
                          queryableDates.getQueryableDates(),
                          originalSender));
            })
        .matchAny(o -> log.info("received unknown message"))
        .build();
  }

  // TODO: clean up duplicated code
  private Receive collectingResponses(
      final ImmutableSet<String> collectedDateCancellationResponses,
      final ImmutableList<LocalDate> cancelledDates,
      final ImmutableSet<String> expectedDateCancellationResponses,
      final ActorRef originalSender) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseResponse.CancellationConfirmation.class,
            cancellationConfirmation -> {
              final LocalDate cancelledDate = cancellationConfirmation.getDate();
              final String cancelledDateAsString = cancelledDate.toString();

              if (expectedDateCancellationResponses.contains(cancelledDateAsString)) {
                final ImmutableList<LocalDate> newCancelledDates =
                    UtilityFunctions.addToImmutableList(cancelledDates, cancelledDate);

                final ImmutableSet<String> newCollectedDateCancellationResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateCancellationResponses, cancelledDateAsString);

                if (newCollectedDateCancellationResponses.containsAll(
                    expectedDateCancellationResponses)) {
                  handleResult(newCancelledDates, originalSender);
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateCancellationResponses,
                              newCancelledDates,
                              expectedDateCancellationResponses,
                              originalSender));
                }
              } else {
                throw new IllegalStateException(
                    "Received response for a date that wasn't expected");
              }
            })
        .match(
            SingleDateDatabaseResponse.DoesntQualifyForCancellationConfirmation.class,
            doesntQualifyForCancellationConfirmation -> {
              final LocalDate notQualifyingForCancellationConfirmationDate =
                  doesntQualifyForCancellationConfirmation.getDate();
              final String notQualifyingForCancellationConfirmationDateAsString =
                  notQualifyingForCancellationConfirmationDate.toString();

              if (expectedDateCancellationResponses.contains(
                  notQualifyingForCancellationConfirmationDateAsString)) {

                final ImmutableSet<String> newCollectedDateCancellationResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateCancellationResponses,
                        notQualifyingForCancellationConfirmationDateAsString);

                if (newCollectedDateCancellationResponses.containsAll(
                    expectedDateCancellationResponses)) {
                  handleResult(cancelledDates, originalSender);
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateCancellationResponses,
                              cancelledDates,
                              expectedDateCancellationResponses,
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
      final ImmutableList<LocalDate> cancelledDates, final ActorRef originalSender) {
    if (cancelledDates.isEmpty()) {
      final RequestHandlerResponse.Failure failure =
          RequestHandlerResponse.Failure.failed(
              APIError.BookingIdNotFoundError, APIErrorMessages.ENGLISH);
      // Inform sender of failure
      originalSender.tell(failure, self());
    } else {
      originalSender.tell(
          RequestHandlerResponse.Success.succeeded(BookingConfirmation.create(bookingId)), self());
    }
    // We are done handling the request this actor will suicide
    self().tell(PoisonPill.getInstance(), self());
  }
}
