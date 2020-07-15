package com.rimanware.volcanoisland.database;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.rimanware.volcanoisland.common.*;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseCommand;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseResponse;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.BiFunction;

public final class RollingMonthDatabaseActor extends LoggingReceiveActor {

  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
  private final Optional<String> databaseFolderPath;
  private final BookingConstraints bookingConstraints;
  private final BiFunction<LocalDate, Optional<String>, Props> singleDateDatabaseActorProps;

  private RollingMonthDatabaseActor(
      final Optional<String> databaseFolderPath,
      final BookingConstraints bookingConstraints,
      final BiFunction<LocalDate, Optional<String>, Props> singleDateDatabaseActorProps) {
    this.databaseFolderPath = databaseFolderPath;
    this.bookingConstraints = bookingConstraints;
    this.singleDateDatabaseActorProps = singleDateDatabaseActorProps;
  }

  private static RollingMonthDatabaseActor createInMemory(
      final BookingConstraints bookingConstraints,
      final BiFunction<LocalDate, Optional<String>, Props> singleDateDatabaseActorProps) {
    return new RollingMonthDatabaseActor(
        Optional.empty(), bookingConstraints, singleDateDatabaseActorProps);
  }

  private static RollingMonthDatabaseActor create(
      final String databaseFolderPath,
      final BookingConstraints bookingConstraints,
      final BiFunction<LocalDate, Optional<String>, Props> singleDateDatabaseActorProps) {
    return new RollingMonthDatabaseActor(
        Optional.of(databaseFolderPath), bookingConstraints, singleDateDatabaseActorProps);
  }

  public static Props propsInMemory(
      final BookingConstraints bookingConstraints,
      final BiFunction<LocalDate, Optional<String>, Props> singleDateDatabaseActorProps) {
    return Props.create(
        RollingMonthDatabaseActor.class,
        () ->
            RollingMonthDatabaseActor.createInMemory(
                bookingConstraints, singleDateDatabaseActorProps));
  }

  public static Props props(
      final String databaseFolderPath,
      final BookingConstraints bookingConstraints,
      final BiFunction<LocalDate, Optional<String>, Props> singleDateDatabaseActorProps) {
    return Props.create(
        RollingMonthDatabaseActor.class,
        () ->
            RollingMonthDatabaseActor.create(
                databaseFolderPath, bookingConstraints, singleDateDatabaseActorProps));
  }

  private Receive inactive() {
    return receiveBuilder()
        .match(
            RollingMonthDatabaseCommand.Start.class,
            start -> {
              final LocalDate currentDate = start.getDate();
              final ImmutableList<LocalDate> reservableDays =
                  bookingConstraints.generateAllReservableDays(currentDate);

              // Create all singleDateDatabaseManagerActors one for each day.
              final ImmutableMap<String, ActorRef> dateToSingleDateDatabaseManagerActor =
                  createAllSingleDateDatabaseManagerActors(reservableDays);

              getContext().become(started(currentDate, dateToSingleDateDatabaseManagerActor));
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  // TODO: Make this support date rolling
  // TODO: Clean up duplicate code
  private Receive started(
      final LocalDate currentDate,
      final ImmutableMap<String, ActorRef> dateToSingleDateDatabaseManagerActor) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseCommand.Book.class,
            book ->
                forwardToIntendedDateDatabaseElseReplyToSender(
                    book.getDate(),
                    book,
                    sender(),
                    dateToSingleDateDatabaseManagerActor,
                    currentDate))
        .match(
            SingleDateDatabaseCommand.CancelBooking.class,
            cancelBooking -> {
              // broadcasting to all dates databases
              dateToSingleDateDatabaseManagerActor
                  .values()
                  .forEach(actor -> actor.forward(cancelBooking, getContext()));
            })
        .match(
            SingleDateDatabaseCommand.UpdateBooking.class,
            updateBooking -> {
              final RollingMonthDatabaseResponse.RequestedDatesOutOfRange outOfRangeErrors =
                  extractOutOfRangeErrorsFromUpdateBooking(currentDate, updateBooking);

              sender().tell(outOfRangeErrors, self());

              // broadcasting to all dates databases
              dateToSingleDateDatabaseManagerActor
                  .values()
                  .forEach(actor -> actor.forward(updateBooking, getContext()));
            })
        .match(
            SingleDateDatabaseCommand.Commit.class,
            commit ->
                forwardToIntendedDateDatabaseElseReplyToSender(
                    commit.getDate(),
                    commit,
                    sender(),
                    dateToSingleDateDatabaseManagerActor,
                    currentDate))
        .match(
            SingleDateDatabaseCommand.Revert.class,
            revert ->
                forwardToIntendedDateDatabaseElseReplyToSender(
                    revert.getDate(),
                    revert,
                    sender(),
                    dateToSingleDateDatabaseManagerActor,
                    currentDate))
        .match(
            SingleDateDatabaseCommand.GetAvailability.class,
            getAvailability -> {
              forwardToIntendedDateDatabaseElseReplyToSender(
                  getAvailability.getDate(),
                  getAvailability,
                  sender(),
                  dateToSingleDateDatabaseManagerActor,
                  currentDate);
            })
        .match(
            RollingMonthDatabaseCommand.GetQueryableDates.class,
            request -> {
              sender()
                  .tell(
                      RollingMonthDatabaseResponse.queryableDates(
                          dateToSingleDateDatabaseManagerActor.keySet()),
                      self());
            })
        .match(
            RollingMonthDatabaseCommand.Deactivate.class,
            deactivate -> {
              dateToSingleDateDatabaseManagerActor
                  .values()
                  .forEach(actor -> actor.tell(PoisonPill.getInstance(), self()));
              getContext().become(inactive());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  private <Message> void forwardToIntendedDateDatabaseElseReplyToSender(
          final LocalDate date,
          final Message msg,
          final ActorRef sender,
          final ImmutableMap<String, ActorRef> dateToSingleDateDatabaseManagerActor,
          final LocalDate currentDate) {

    final DateValidator.DateValidation validation =
        DateValidator.isInValidRange(date, currentDate, bookingConstraints);

    if (validation instanceof DateValidator.Valid) {
      final ActorRef destination = singleDateDatabaseOf(dateToSingleDateDatabaseManagerActor, date);
      destination.forward(msg, getContext());
    } else {
      final DateValidator.Invalid invalid = (DateValidator.Invalid) validation;
      sender.tell(RollingMonthDatabaseResponse.outOfRange(date, invalid.getReason()), self());
    }
  }

  private ActorRef singleDateDatabaseOf(
          final ImmutableMap<String, ActorRef> dateToSingleDateDatabaseManagerActor, final LocalDate date) {
    return Optional.ofNullable(dateToSingleDateDatabaseManagerActor.get(date.toString()))
        .orElseThrow(() -> singleDateDatabaseActorReferenceNotFoundFor(date));
  }

  private IllegalStateException singleDateDatabaseActorReferenceNotFoundFor(final LocalDate date) {
    return new IllegalStateException(
        "SingleDateDatabase actor reference for " + "date " + date + " not found");
  }

  private RollingMonthDatabaseResponse.RequestedDatesOutOfRange
      extractOutOfRangeErrorsFromUpdateBooking(
          final LocalDate currentDate,
          final SingleDateDatabaseCommand.UpdateBooking updateBooking) {
    final ImmutableSet<LocalDate> daysToBook =
        UtilityFunctions.generateAllDatesInRange(
            updateBooking.getBooking().getArrivalDate(),
            updateBooking.getBooking().getDepartureDate());

    return RollingMonthDatabaseResponse.outOfRange(
        daysToBook.stream()
            .map(
                dateToBook ->
                    Tuple.create(
                        dateToBook,
                        DateValidator.isInValidRange(dateToBook, currentDate, bookingConstraints)))
            .filter(tuple -> tuple.getRight() instanceof DateValidator.Invalid)
            .map(tuple -> Tuple.create(tuple.getLeft(), (DateValidator.Invalid) tuple.getRight()))
            .map(
                tuple ->
                    RollingMonthDatabaseResponse.outOfRange(
                        tuple.getLeft(), tuple.getRight().getReason()))
            .collect(ImmutableList.toImmutableList()));
  }

  private ImmutableMap<String, ActorRef> createAllSingleDateDatabaseManagerActors(
          final ImmutableList<LocalDate> reservableDays) {
    return reservableDays.stream()
        .map(
            day -> {
              final ActorRef newSingleDateDatabaseActor =
                  getContext()
                      .actorOf(
                          singleDateDatabaseActorProps.apply(day, databaseFolderPath),
                          "SingleDateDatabaseManagerActor-" + day.toString());
              newSingleDateDatabaseActor.tell(
                  SingleDateDatabaseManagerActor.SingleDateDatabaseManagerCommand.start(), self());
              return Tuple.create(day.toString(), newSingleDateDatabaseActor);
            })
        .collect(ImmutableMap.toImmutableMap(Tuple::getLeft, Tuple::getRight));
  }

  @Override
  public Receive createReceive() {
    return inactive();
  }
}
