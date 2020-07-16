package com.rimanware.volcanoisland.services.requesthandlers;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.rimanware.volcanoisland.common.UtilityFunctions;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseResponse;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseResponse;
import com.rimanware.volcanoisland.errors.api.APIErrorMessages;
import com.rimanware.volcanoisland.services.models.requests.AvailabilitiesRequest;
import com.rimanware.volcanoisland.services.models.responses.Availabilities;
import com.rimanware.volcanoisland.services.models.responses.Availability;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerCommand;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;
import com.rimanware.volcanoisland.services.requesthandlers.common.RequestHandlerActor;
import com.rimanware.volcanoisland.services.requesthandlers.common.ResponseCollector;
import com.rimanware.volcanoisland.services.requesthandlers.common.SenderProvider;

import java.time.LocalDate;

public final class AvailabilityRequestHandlerActor
    extends RequestHandlerActor<AvailabilityRequestHandlerActor.AvailabilityRequestState> {
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
      final AvailabilitiesRequest availabilitiesRequest,
      final APIErrorMessages apiErrorMessages,
      final ActorRef database) {
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
                  UtilityFunctions.generateAllDatesInRange(
                      availabilitiesRequest.getStartDate(), availabilitiesRequest.getEndDate());

              daysToQuery.forEach(
                  day -> database.tell(SingleDateDatabaseCommand.getAvailability(day), self()));

              getContext()
                  .become(
                      collectingResponses(
                          ResponseCollector.empty(
                              daysToQuery.stream()
                                  .map(LocalDate::toString)
                                  .collect(ImmutableSet.toImmutableSet())),
                          AvailabilityRequestState.empty(sender)));
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  @Override
  protected Receive collectingResponses(
      final ResponseCollector<String> currentResponseCollector,
      final AvailabilityRequestState currentAvailabilityRequestState) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseResponse.IsAvailable.class,
            isAvailable -> {
              final LocalDate availableDate = isAvailable.getDate();
              final String availableDateAsString = availableDate.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(availableDateAsString);
              final AvailabilityRequestState newAvailabilityRequestState =
                  currentAvailabilityRequestState.addAvailableLocalDate(availableDate);

              nextStateOrCompleteRequest(newResponseCollector, newAvailabilityRequestState);
            })
        .match(
            SingleDateDatabaseResponse.IsBooked.class,
            isBooked -> {
              final LocalDate bookedDate = isBooked.getDate();
              final String bookedDateAsString = bookedDate.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(bookedDateAsString);

              nextStateOrCompleteRequest(newResponseCollector, currentAvailabilityRequestState);
            })
        .match(
            RollingMonthDatabaseResponse.RequestedDateOutOfRange.class,
            requestedDateOutOfRange -> {
              final LocalDate dateOutOfRange = requestedDateOutOfRange.getRequestedDate();
              final String dateOutOfRangeAsString = dateOutOfRange.toString();

              final ResponseCollector<String> newResponseCollector =
                  currentResponseCollector.collect(dateOutOfRangeAsString);

              nextStateOrCompleteRequest(newResponseCollector, currentAvailabilityRequestState);
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  @Override
  protected RequestHandlerResponse createResponse(
      final AvailabilityRequestHandlerActor.AvailabilityRequestState availabilityRequestState) {
    return RequestHandlerResponse.Success.succeeded(
        Availabilities.create(
            availabilityRequestState.getAvailableLocalDates().stream()
                .sorted()
                .map(Availability::create)
                .collect(ImmutableList.toImmutableList())));
  }

  protected static class AvailabilityRequestState implements SenderProvider {
    private final ImmutableList<LocalDate> availableLocalDates;
    private final ActorRef sender;

    private AvailabilityRequestState(
        final ImmutableList<LocalDate> availableLocalDates, final ActorRef sender) {
      this.availableLocalDates = availableLocalDates;
      this.sender = sender;
    }

    public static AvailabilityRequestState empty(final ActorRef sender) {
      return create(ImmutableList.of(), sender);
    }

    private static AvailabilityRequestState create(
        final ImmutableList<LocalDate> availableLocalDates, final ActorRef sender) {
      return new AvailabilityRequestState(availableLocalDates, sender);
    }

    public AvailabilityRequestState addAvailableLocalDates(
        final ImmutableList<LocalDate> newAvailableLocalDates) {
      return create(UtilityFunctions.combine(availableLocalDates, newAvailableLocalDates), sender);
    }

    public AvailabilityRequestState addAvailableLocalDate(final LocalDate newAvailableLocalDate) {
      return addAvailableLocalDates(ImmutableList.of(newAvailableLocalDate));
    }

    public ImmutableList<LocalDate> getAvailableLocalDates() {
      return availableLocalDates;
    }

    @Override
    public ActorRef getSender() {
      return sender;
    }
  }
}
