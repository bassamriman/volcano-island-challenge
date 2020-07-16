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
import com.rimanware.volcanoisland.errors.APIErrorImpl;
import com.rimanware.volcanoisland.errors.api.APIErrorMessages;
import com.rimanware.volcanoisland.services.models.responses.BookingConfirmation;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerCommand;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;
import com.rimanware.volcanoisland.services.requesthandlers.common.RequestHandlerActor;
import com.rimanware.volcanoisland.services.requesthandlers.common.ResponseCollector;
import com.rimanware.volcanoisland.services.requesthandlers.common.SenderProvider;

import java.time.LocalDate;

public final class DeleteBookingRequestHandlerActor
    extends RequestHandlerActor<DeleteBookingRequestHandlerActor.DeleteRequestState> {
  private final APIErrorMessages apiErrorMessages;
  private final String bookingId;
  private final ActorRef database;

  private DeleteBookingRequestHandlerActor(
      final String bookingId, final APIErrorMessages apiErrorMessages, final ActorRef database) {
    this.apiErrorMessages = apiErrorMessages;
    this.bookingId = bookingId;
    this.database = database;
  }

  private static DeleteBookingRequestHandlerActor create(
      final String bookingId, final APIErrorMessages apiErrorMessages, final ActorRef database) {
    return new DeleteBookingRequestHandlerActor(bookingId, apiErrorMessages, database);
  }

  public static Props props(
      final String bookingId, final APIErrorMessages apiErrorMessages, final ActorRef database) {
    return Props.create(
        DeleteBookingRequestHandlerActor.class,
        () -> DeleteBookingRequestHandlerActor.create(bookingId, apiErrorMessages, database));
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
              database.tell(SingleDateDatabaseCommand.cancel(bookingId), self());

              getContext()
                  .become(
                      collectingResponses(
                          ResponseCollector.empty(
                              queryableDates.getQueryableDates().stream()
                                  .map(LocalDate::toString)
                                  .collect(ImmutableSet.toImmutableSet())),
                          DeleteRequestState.empty(originalSender)));
            })
        .matchAny(o -> log.info("received unknown message"))
        .build();
  }

  @Override
  protected Receive collectingResponses(
      final ResponseCollector<String> currentResponseCollector,
      final DeleteRequestState currentDeleteRequestState) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseResponse.CancellationConfirmation.class,
            cancellationConfirmation -> {
              final LocalDate cancelledDate = cancellationConfirmation.getDate();
              final String cancelledDateAsString = cancelledDate.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(cancelledDateAsString);
              final DeleteRequestState newDeleteRequestState =
                  currentDeleteRequestState.addCancelledDate(cancelledDate);

              nextStateOrCompleteRequest(newResponseCollector, newDeleteRequestState);
            })
        .match(
            SingleDateDatabaseResponse.DoesntQualifyForCancellationConfirmation.class,
            doesntQualifyForCancellationConfirmation -> {
              final LocalDate notQualifyingForCancellationConfirmationDate =
                  doesntQualifyForCancellationConfirmation.getDate();
              final String notQualifyingForCancellationConfirmationDateAsString =
                  notQualifyingForCancellationConfirmationDate.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(
                      notQualifyingForCancellationConfirmationDateAsString);

              nextStateOrCompleteRequest(newResponseCollector, currentDeleteRequestState);
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  @Override
  protected RequestHandlerResponse createResponse(final DeleteRequestState deleteRequestState) {
    if (deleteRequestState.getCancelledDates().isEmpty()) {
      return RequestHandlerResponse.Failure.failed(
          APIErrorImpl.BookingIdNotFoundError, apiErrorMessages);
    } else {
      return RequestHandlerResponse.Success.succeeded(BookingConfirmation.create(bookingId));
    }
  }

  protected static class DeleteRequestState implements SenderProvider {
    private final ImmutableList<LocalDate> cancelledDates;
    private final ActorRef sender;

    private DeleteRequestState(
        final ImmutableList<LocalDate> cancelledDates, final ActorRef sender) {
      this.cancelledDates = cancelledDates;
      this.sender = sender;
    }

    public static DeleteRequestState empty(final ActorRef sender) {
      return create(ImmutableList.of(), sender);
    }

    private static DeleteRequestState create(
        final ImmutableList<LocalDate> newCancelledDates, final ActorRef sender) {
      return new DeleteRequestState(newCancelledDates, sender);
    }

    public DeleteRequestState addCancelledDates(final ImmutableList<LocalDate> newCancelledDates) {
      return create(UtilityFunctions.combine(cancelledDates, newCancelledDates), sender);
    }

    public DeleteRequestState addCancelledDate(final LocalDate newCancelledDate) {
      return addCancelledDates(ImmutableList.of(newCancelledDate));
    }

    public ImmutableList<LocalDate> getCancelledDates() {
      return cancelledDates;
    }

    @Override
    public ActorRef getSender() {
      return sender;
    }
  }
}
