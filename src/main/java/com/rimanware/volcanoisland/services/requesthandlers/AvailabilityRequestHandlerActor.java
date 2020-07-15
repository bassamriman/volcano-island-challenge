package com.rimanware.volcanoisland.services.requesthandlers;

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
import com.rimanware.volcanoisland.services.models.requests.AvailabilitiesRequest;
import com.rimanware.volcanoisland.services.models.responses.Availabilities;
import com.rimanware.volcanoisland.services.models.responses.Availability;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerCommand;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

public final class AvailabilityRequestHandlerActor extends LoggingReceiveActor {
  private final AvailabilitiesRequest availabilitiesRequest;
  private final ActorRef database;

  private AvailabilityRequestHandlerActor(
      final AvailabilitiesRequest availabilitiesRequest, final ActorRef database) {
    this.availabilitiesRequest = availabilitiesRequest;
    this.database = database;
  }

  public static AvailabilityRequestHandlerActor create(
      final AvailabilitiesRequest availabilitiesRequest, final ActorRef database) {
    return new AvailabilityRequestHandlerActor(availabilitiesRequest, database);
  }

  public static Props props(
      final AvailabilitiesRequest availabilitiesRequest, final ActorRef database) {
    return Props.create(
        AvailabilityRequestHandlerActor.class,
        () -> AvailabilityRequestHandlerActor.create(availabilitiesRequest, database));
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

              final ImmutableSet<LocalDate> daysToQuery =
                  Stream.iterate(availabilitiesRequest.getStartDate(), d -> d.plusDays(1))
                      .limit(
                          ChronoUnit.DAYS.between(
                              availabilitiesRequest.getStartDate(),
                              // Increment by one because ChronoUnit.DAYS.between API
                              // to date is exclusive
                              availabilitiesRequest.getEndDate().plusDays(1)))
                      .collect(ImmutableSet.toImmutableSet());

              daysToQuery.forEach(
                  day -> database.tell(SingleDateDatabaseCommand.getAvailability(day), self()));

              getContext()
                  .become(
                      collectingResponses(
                          ImmutableSet.of(),
                          ImmutableList.of(),
                          daysToQuery.stream()
                              .map(LocalDate::toString)
                              .collect(ImmutableSet.toImmutableSet()),
                          sender));
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  // TODO: clean up duplicated code
  private Receive collectingResponses(
      final ImmutableSet<String> collectedDateQueryResponses,
      final ImmutableList<LocalDate> availableLocalDates,
      final ImmutableSet<String> expectedDateQueryResponses,
      final ActorRef sender) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseResponse.IsAvailable.class,
            isAvailable -> {
              final LocalDate availableDate = isAvailable.getDate();
              final String availableDateAsString = availableDate.toString();

              if (expectedDateQueryResponses.contains(availableDateAsString)) {
                final ImmutableList<LocalDate> newAvailableLocalDates =
                    UtilityFunctions.addToImmutableList(availableLocalDates, availableDate);

                final ImmutableSet<String> newCollectedDateQueryResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateQueryResponses, availableDateAsString);

                if (newCollectedDateQueryResponses.containsAll(expectedDateQueryResponses)) {
                  sender.tell(
                      RequestHandlerResponse.Success.succeeded(
                          Availabilities.create(
                              newAvailableLocalDates.stream()
                                  .sorted()
                                  .map(Availability::create)
                                  .collect(ImmutableList.toImmutableList()))),
                      self());

                  // We are done handling the request this actor will suicide
                  self().tell(PoisonPill.getInstance(), self());
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateQueryResponses,
                              newAvailableLocalDates,
                              expectedDateQueryResponses,
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

              if (expectedDateQueryResponses.contains(bookedDateAsString)) {
                final ImmutableSet<String> newCollectedDateQueryResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateQueryResponses, bookedDateAsString);

                if (newCollectedDateQueryResponses.containsAll(expectedDateQueryResponses)) {
                  sender.tell(
                      RequestHandlerResponse.Success.succeeded(
                          Availabilities.create(
                              availableLocalDates.stream()
                                  .sorted()
                                  .map(Availability::create)
                                  .collect(ImmutableList.toImmutableList()))),
                      self());

                  // We are done handling the request this actor will suicide
                  self().tell(PoisonPill.getInstance(), self());
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateQueryResponses,
                              availableLocalDates,
                              expectedDateQueryResponses,
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

              if (expectedDateQueryResponses.contains(dateOutOfRangeAsString)) {
                final ImmutableSet<String> newCollectedDateQueryResponses =
                    UtilityFunctions.addToImmutableSet(
                        collectedDateQueryResponses, dateOutOfRangeAsString);

                if (newCollectedDateQueryResponses.containsAll(expectedDateQueryResponses)) {
                  sender.tell(
                      RequestHandlerResponse.Success.succeeded(
                          Availabilities.create(
                              availableLocalDates.stream()
                                  .sorted()
                                  .map(Availability::create)
                                  .collect(ImmutableList.toImmutableList()))),
                      self());

                  // We are done handling the request this actor will suicide
                  self().tell(PoisonPill.getInstance(), self());
                } else {
                  getContext()
                      .become(
                          collectingResponses(
                              newCollectedDateQueryResponses,
                              availableLocalDates,
                              expectedDateQueryResponses,
                              sender));
                }

              } else {
                throw new IllegalStateException(
                    "Received response for a date that wasn't expected");
              }
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }
}
