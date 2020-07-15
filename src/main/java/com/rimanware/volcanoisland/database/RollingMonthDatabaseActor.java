package com.rimanware.volcanoisland.database;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.rimanware.volcanoisland.common.BookingConstraints;
import com.rimanware.volcanoisland.common.DateValidator;
import com.rimanware.volcanoisland.common.LoggingReceiveActor;
import com.rimanware.volcanoisland.common.Tuple;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseCommand;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseResponse;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

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
                  reservableDays.stream()
                      .map(
                          day -> {
                            final ActorRef newSingleDateDatabaseActor =
                                getContext()
                                    .actorOf(
                                        singleDateDatabaseActorProps.apply(day, databaseFolderPath),
                                        "SingleDateDatabaseManagerActor-" + day.toString());
                            newSingleDateDatabaseActor.tell(
                                SingleDateDatabaseManagerActor.SingleDateDatabaseManagerCommand
                                    .start(),
                                self());
                            return Tuple.create(day.toString(), newSingleDateDatabaseActor);
                          })
                      .collect(ImmutableMap.toImmutableMap(Tuple::getLeft, Tuple::getRight));

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
            book -> {
              final ActorRef sender = sender();
              final DateValidator.DateValidation validation =
                  DateValidator.isValid(book.getDate(), currentDate, bookingConstraints);

              if (validation instanceof DateValidator.Valid) {
                final ActorRef actor =
                    Optional.ofNullable(
                            dateToSingleDateDatabaseManagerActor.get(book.getDate().toString()))
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "SingleDateDatabase actor reference for "
                                        + "date "
                                        + book.getDate()
                                        + " not found"));
                actor.forward(book, getContext());
              } else {
                final DateValidator.Invalid invalid = (DateValidator.Invalid) validation;
                sender.tell(
                    RollingMonthDatabaseResponse.outOfRange(book.getDate(), invalid.getReason()),
                    self());
              }
            })
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
            commit -> {
              final ActorRef sender = sender();
              final DateValidator.DateValidation validation =
                  DateValidator.isValid(commit.getDate(), currentDate, bookingConstraints);
              if (validation instanceof DateValidator.Valid) {
                final ActorRef actor =
                    Optional.ofNullable(
                            dateToSingleDateDatabaseManagerActor.get(commit.getDate().toString()))
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "SingleDateDatabase actor reference for "
                                        + "date "
                                        + commit.getDate()
                                        + " not found"));
                actor.forward(commit, getContext());
              } else {
                final DateValidator.Invalid invalid = (DateValidator.Invalid) validation;
                sender.tell(
                    RollingMonthDatabaseResponse.outOfRange(commit.getDate(), invalid.getReason()),
                    self());
              }
            })
        .match(
            SingleDateDatabaseCommand.Revert.class,
            revert -> {
              final ActorRef sender = sender();
              final DateValidator.DateValidation validation =
                  DateValidator.isValid(revert.getDate(), currentDate, bookingConstraints);
              if (validation instanceof DateValidator.Valid) {
                final ActorRef actor =
                    Optional.ofNullable(
                            dateToSingleDateDatabaseManagerActor.get(revert.getDate().toString()))
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "SingleDateDatabase actor reference for "
                                        + "date "
                                        + revert.getDate()
                                        + " not found"));
                actor.forward(revert, getContext());
              } else {
                final DateValidator.Invalid invalid = (DateValidator.Invalid) validation;
                sender.tell(
                    RollingMonthDatabaseResponse.outOfRange(revert.getDate(), invalid.getReason()),
                    self());
              }
            })
        .match(
            SingleDateDatabaseCommand.GetAvailability.class,
            getAvailability -> {
              final ActorRef sender = sender();
              final DateValidator.DateValidation validation =
                  DateValidator.isValid(getAvailability.getDate(), currentDate, bookingConstraints);

              if (validation instanceof DateValidator.Valid) {
                final ActorRef actor =
                    Optional.ofNullable(
                            dateToSingleDateDatabaseManagerActor.get(
                                getAvailability.getDate().toString()))
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "SingleDateDatabase actor reference for "
                                        + "date "
                                        + getAvailability.getDate()
                                        + " not found"));
                actor.forward(getAvailability, getContext());
              } else {
                final DateValidator.Invalid invalid = (DateValidator.Invalid) validation;
                sender.tell(
                    RollingMonthDatabaseResponse.outOfRange(
                        getAvailability.getDate(), invalid.getReason()),
                    self());
              }
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

  private RollingMonthDatabaseResponse.RequestedDatesOutOfRange
      extractOutOfRangeErrorsFromUpdateBooking(
          final LocalDate currentDate,
          final SingleDateDatabaseCommand.UpdateBooking updateBooking) {
    final ImmutableSet<LocalDate> daysToBook =
        Stream.iterate(updateBooking.getBooking().getArrivalDate(), d -> d.plusDays(1))
            .limit(
                ChronoUnit.DAYS.between(
                    updateBooking.getBooking().getArrivalDate(),
                    // Increment by one because ChronoUnit.DAYS.between API
                    // to date is exclusive
                    updateBooking.getBooking().getDepartureDate().plusDays(1)))
            .collect(ImmutableSet.toImmutableSet());

    return RollingMonthDatabaseResponse.outOfRange(
        daysToBook.stream()
            .map(
                dateToBook ->
                    Tuple.create(
                        dateToBook,
                        DateValidator.isValid(dateToBook, currentDate, bookingConstraints)))
            .filter(tuple -> tuple.getRight() instanceof DateValidator.Invalid)
            .map(tuple -> Tuple.create(tuple.getLeft(), (DateValidator.Invalid) tuple.getRight()))
            .map(
                tuple ->
                    RollingMonthDatabaseResponse.outOfRange(
                        tuple.getLeft(), tuple.getRight().getReason()))
            .collect(ImmutableList.toImmutableList()));
  }

  @Override
  public Receive createReceive() {
    // Defaults to available as the original date database will inform it eventually of initial
    // state
    return inactive();
  }
}
