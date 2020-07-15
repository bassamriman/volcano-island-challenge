package com.rimanware.volcanoisland.database;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.ImmutableList;
import com.rimanware.volcanoisland.common.LoggingReceiveActor;
import com.rimanware.volcanoisland.common.UtilityFunctions;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public final class SingleDateDatabaseManagerActor extends LoggingReceiveActor {

  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
  private final String ioDispatcher = "akka.actor.blocking-io-dispatcher";
  private final LocalDate date;
  private final Optional<String> maybeDatabaseFolderPath;

  private SingleDateDatabaseManagerActor(
      final LocalDate date, final Optional<String> maybeDatabaseFolderPath) {
    this.maybeDatabaseFolderPath = maybeDatabaseFolderPath;
    this.date = date;
  }

  private static SingleDateDatabaseManagerActor create(
      final LocalDate date, final Optional<String> maybeDatabaseFolderPath) {
    return new SingleDateDatabaseManagerActor(date, maybeDatabaseFolderPath);
  }

  public static Props props(final LocalDate date, final Optional<String> maybeDatabaseFolderPath) {
    return Props.create(
        SingleDateDatabaseManagerActor.class,
        () -> SingleDateDatabaseManagerActor.create(date, maybeDatabaseFolderPath));
  }

  private static Props getWriteSingleDateDatabaseProps(
      final Optional<String> maybeDatabaseFolderPath,
      final LocalDate date,
      final ActorRef readReplicaActor)
      throws IOException {
    if (maybeDatabaseFolderPath.isPresent()) {
      return SingleDateDatabaseActor.props(date, maybeDatabaseFolderPath.get(), readReplicaActor);
    } else {
      return SingleDateDatabaseActor.inMemoryProps(date, readReplicaActor);
    }
  }

  private Receive inactive() {
    return receiveBuilder()
        .match(
            Start.class,
            start -> {
              final ActorRef readReplicaActor =
                  getContext()
                      .actorOf(
                          SingleDateDatabaseReadReplicaActor.props(date),
                          "ReadReplicaSingleDateDatabase-" + date.toString());
              final ActorRef writeReadActor =
                  getContext()
                      .actorOf(
                          getWriteSingleDateDatabaseProps(
                                  maybeDatabaseFolderPath, date, readReplicaActor)
                              .withDispatcher(ioDispatcher),
                          "WriteSingleDateDatabase-" + date.toString());
              getContext().become(started(ImmutableList.of(), writeReadActor, readReplicaActor));
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  private Receive started(
      final ImmutableList<Request> requests,
      final ActorRef writeReadActor,
      final ActorRef readReplicaActor) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseCommand.Book.class,
            book -> {
              // Store request before asking the read replica if date is available,
              // this is an optimisation to reduce load database writer
              final Request request = Request.create(sender(), book);
              final ImmutableList<Request> newRequests =
                  UtilityFunctions.addToImmutableList(requests, request);
              readReplicaActor.tell(SingleDateDatabaseCommand.getAvailability(date), self());
              getContext().become(started(newRequests, writeReadActor, readReplicaActor));
            })
        .match(
            SingleDateDatabaseCommand.UpdateBooking.class,
            updateBooking -> {
              // We want to updates to go to writer right away as it's high priority.
              writeReadActor.forward(updateBooking, getContext());
            })
        .match(
            SingleDateDatabaseCommand.CancelBooking.class,
            cancelBooking -> {
              // We want to cancellations to go to writer right away as it's high priority.
              writeReadActor.forward(cancelBooking, getContext());
            })
        .match(
            SingleDateDatabaseCommand.Commit.class,
            commit -> {
              // We want to commits to go to writer right away as it's high priority.
              writeReadActor.forward(commit, getContext());
            })
        .match(
            SingleDateDatabaseCommand.Revert.class,
            revert -> {
              // We want to reverts to go to writer right away as it's high priority.
              writeReadActor.forward(revert, getContext());
            })
        .match(
            SingleDateDatabaseCommand.GetAvailability.class,
            // Forward to read replica to remove load from writer date database. We expect more
            // reads than write.
            getAvailability -> readReplicaActor.forward(getAvailability, getContext()))
        .match(
            SingleDateDatabaseResponse.IsAvailable.class,
            isAvailable -> {
              // The readReplica replied that the date is available hence we will forward only
              // the first booking requests to database writer for persistence.
              requests.stream()
                  .findFirst()
                  .ifPresent(
                      (request ->
                          writeReadActor.tell(request.getBookingRequest(), request.getSender())));

              // Reject the remaining requests as the first one was elected for booking.
              requests.stream()
                  .skip(1)
                  .forEach(
                      request ->
                          request
                              .getSender()
                              .tell(SingleDateDatabaseResponse.isBooked(date), self()));

              // Update actor state by flushing all requests as they've been handled.
              getContext().become(started(ImmutableList.of(), writeReadActor, readReplicaActor));
            })
        .match(
            SingleDateDatabaseResponse.IsBooked.class,
            isAvailable -> {

              // Reject all requests as date is already booked.
              requests.forEach(
                  request ->
                      request.getSender().tell(SingleDateDatabaseResponse.isBooked(date), self()));

              // Update actor state by flushing all requests as they've been handled.
              getContext().become(started(ImmutableList.of(), writeReadActor, readReplicaActor));
            })
        .match(
            Deactivate.class,
            deactivate -> {
              // Kill children
              writeReadActor.tell(PoisonPill.getInstance(), self());
              readReplicaActor.tell(PoisonPill.getInstance(), self());
              getContext().become(inactive());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  @Override
  public Receive createReceive() {
    // Defaults to available as the original date database will inform it eventually of initial
    // state
    return inactive();
  }

  public interface SingleDateDatabaseManagerCommand {

    static Start start() {
      return Start.INSTANCE;
    }

    static Deactivate deactivate() {
      return Deactivate.INSTANCE;
    }
  }

  enum Start implements SingleDateDatabaseManagerCommand {
    INSTANCE;

    Start() {}

    @Override
    public String toString() {
      return "Start{}";
    }
  }

  enum Deactivate implements SingleDateDatabaseManagerCommand {
    INSTANCE;

    Deactivate() {}

    @Override
    public String toString() {
      return "Deactivate{}";
    }
  }

  static final class Request {
    private final String id;
    private final ActorRef sender;
    private final SingleDateDatabaseCommand.Book bookingRequest;

    private Request(
        final String id,
        final ActorRef sender,
        final SingleDateDatabaseCommand.Book bookingRequest) {
      this.id = id;
      this.sender = sender;
      this.bookingRequest = bookingRequest;
    }

    public static Request create(
        final String id,
        final ActorRef sender,
        final SingleDateDatabaseCommand.Book bookingRequest) {
      return new Request(id, sender, bookingRequest);
    }

    public static Request create(
        final ActorRef sender, final SingleDateDatabaseCommand.Book bookingRequest) {
      return new Request(UUID.randomUUID().toString(), sender, bookingRequest);
    }

    @Override
    public String toString() {
      return "Request{"
          + "id='"
          + id
          + '\''
          + ", sender="
          + sender
          + ", bookingRequest="
          + bookingRequest
          + '}';
    }

    public String getId() {
      return id;
    }

    public ActorRef getSender() {
      return sender;
    }

    public SingleDateDatabaseCommand.Book getBookingRequest() {
      return bookingRequest;
    }
  }
}
