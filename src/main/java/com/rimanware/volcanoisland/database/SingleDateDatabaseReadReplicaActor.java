package com.rimanware.volcanoisland.database;

import akka.actor.Props;
import com.rimanware.volcanoisland.common.LoggingReceiveActor;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseResponse;

import java.time.LocalDate;

public final class SingleDateDatabaseReadReplicaActor extends LoggingReceiveActor {

  private final LocalDate date;

  private SingleDateDatabaseReadReplicaActor(final LocalDate date) {
    this.date = date;
  }

  private static SingleDateDatabaseReadReplicaActor create(final LocalDate date) {
    return new SingleDateDatabaseReadReplicaActor(date);
  }

  public static Props props(final LocalDate date) {
    return Props.create(
        SingleDateDatabaseReadReplicaActor.class,
        () -> SingleDateDatabaseReadReplicaActor.create(date));
  }

  private Receive booked() {
    return receiveBuilder()
        .match(
            SingleDateDatabaseCommand.CancelBooking.class,
            cancelBooking -> {
              getContext().become(available());
            })
        .match(
            SingleDateDatabaseCommand.GetAvailability.class,
            msg -> {
              sender().tell(SingleDateDatabaseResponse.isBooked(date), self());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  private Receive available() {
    return receiveBuilder()
        .match(
            SingleDateDatabaseCommand.Book.class,
            book -> {
              getContext().become(booked());
            })
        .match(
            SingleDateDatabaseCommand.GetAvailability.class,
            msg -> {
              sender().tell(SingleDateDatabaseResponse.isAvailable(date), self());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  @Override
  public Receive createReceive() {
    // Defaults to available as the writer database will inform it eventually of initial state
    return available();
  }
}
