package com.rimanware.volcanoisland.database;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.ImmutableList;
import com.rimanware.volcanoisland.common.LoggingReceiveActor;
import com.rimanware.volcanoisland.common.UtilityFunctions;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseResponse;
import com.rimanware.volcanoisland.database.models.Booking;
import com.rimanware.volcanoisland.database.models.SingleDateDatabaseEvent;

import java.io.*;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

public final class SingleDateDatabaseActor extends LoggingReceiveActor {
  private static final String
      ERROR_OCCURRED_WHILE_WRITING_DATE_DATABASE_EVENT_TO_OBJECT_OUTPUT_STREAM =
          "Error occurred while writing DateDatabaseEvent to ObjectOutputStream: ";
  private static final String SINGLE_DATE_DATABASE_CORRUPTED_ERROR_MESSAGE =
      "SingleDateDatabase file not properly initialized or is corrupted. "
          + "File should contain at least one event. "
          + "File should be initialized with NoBooking DateDatabaseEvent.";
  private static final String SINGLE_DATE_DATABASE_FILE_DOESN_T_EXIST_NEEDS_TO_BE_CREATED =
      "SingleDateDatabase file doesn't exist. Needs to be created";
  private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
  private final LocalDate date;
  private final Receive initialBehaviour;
  private final ObjectOutputStream outputStream;
  private final Supplier<ObjectInputStream> inputStreamSupplier;
  private final ActorRef readReplica;

  private SingleDateDatabaseActor(
      final LocalDate date,
      final SingleDateDatabaseEvent latestDatabaseEvent,
      final ObjectOutputStream outputStream,
      final Supplier<ObjectInputStream> inputStreamSupplier,
      final ActorRef readReplica) {
    this.date = date;
    if (latestDatabaseEvent instanceof SingleDateDatabaseEvent.Booked) {
      final SingleDateDatabaseEvent.Booked booked =
          (SingleDateDatabaseEvent.Booked) latestDatabaseEvent;
      this.initialBehaviour = booked(booked.getBooking());
      // Inform read replica latest booked state read from stream
      readReplica.tell(
          SingleDateDatabaseCommand.book(booked.getBooking(), date), ActorRef.noSender());
    } else {
      this.initialBehaviour = available();
    }
    this.outputStream = outputStream;
    this.inputStreamSupplier = inputStreamSupplier;
    this.readReplica = readReplica;
  }

  private static SingleDateDatabaseActor create(
      final LocalDate date,
      final SingleDateDatabaseEvent latestDatabaseEvent,
      final ObjectOutputStream outputStream,
      final Supplier<ObjectInputStream> inputStreamSupplier,
      final ActorRef readReplica) {
    return new SingleDateDatabaseActor(
        date, latestDatabaseEvent, outputStream, inputStreamSupplier, readReplica);
  }

  public static Props props(
      final LocalDate date,
      final SingleDateDatabaseEvent latestDatabaseEvent,
      final OutputStream outputStream,
      final Supplier<InputStream> inputStreamSupplier,
      final ActorRef readReplica) {
    return Props.create(
        SingleDateDatabaseActor.class,
        () ->
            SingleDateDatabaseActor.create(
                date,
                latestDatabaseEvent,
                new ObjectOutputStream(outputStream) {
                  // This is overridden to allow for appending of new object at end of file
                  protected void writeStreamHeader() throws IOException {
                    reset();
                  }
                },
                () -> {
                  try {
                    return new ObjectInputStream(inputStreamSupplier.get());
                  } catch (final IOException e) {
                    throw new IllegalStateException(SINGLE_DATE_DATABASE_CORRUPTED_ERROR_MESSAGE);
                  }
                },
                readReplica));
  }

  public static Props props(
      final LocalDate date, final String databaseFolderPath, final ActorRef readReplica)
      throws IOException {
    final String singleDateDatabaseFilePath = databaseFolderPath + "/" + date.toString() + ".data";

    final File singleDateDatabaseFile = new File(singleDateDatabaseFilePath);

    final SingleDateDatabaseEvent latestDatabaseEvent;
    if (!singleDateDatabaseFile.isFile()) {
      initialiseFile(singleDateDatabaseFilePath);
      latestDatabaseEvent = SingleDateDatabaseEvent.noBooking();
    } else {
      latestDatabaseEvent = readLastDatabaseEventFromFile(singleDateDatabaseFilePath);
    }

    return props(
        date,
        latestDatabaseEvent,
        new FileOutputStream(singleDateDatabaseFilePath, true),
        () -> {
          try {
            return new FileInputStream(singleDateDatabaseFilePath);
          } catch (final FileNotFoundException e) {
            throw new IllegalStateException(
                SINGLE_DATE_DATABASE_FILE_DOESN_T_EXIST_NEEDS_TO_BE_CREATED);
          }
        },
        readReplica);
  }

  public static Props inMemoryProps(final LocalDate date, final ActorRef readReplica) {

    final SingleDateDatabaseEvent latestDatabaseEvent = SingleDateDatabaseEvent.noBooking();

    return props(
        date,
        latestDatabaseEvent,
        new ByteArrayOutputStream(),
        () -> new ByteArrayInputStream("".getBytes()),
        readReplica);
  }

  /**
   * Create file with NoBooking event written first (mainly to write header of ObjectOutputStream
   * for the first time to avoid getting java.io.StreamCorruptedException on opening the file with
   * ObjectInputStream).
   *
   * @param dateDatabaseFilePath File path used to create the file
   * @throws IOException
   */
  public static void initialiseFile(final String dateDatabaseFilePath) throws IOException {
    final File dateDatabaseFile = new File(dateDatabaseFilePath);
    // Create Parent directories if directories are not already created
    dateDatabaseFile.getParentFile().mkdirs();
    final ObjectOutputStream objectOutputStream =
        new ObjectOutputStream(new FileOutputStream(dateDatabaseFile));
    objectOutputStream.writeObject(SingleDateDatabaseEvent.noBooking());
    objectOutputStream.close();
  }

  public static SingleDateDatabaseEvent readLastDatabaseEventFromFile(
      final String dateDatabaseFilePath) throws IOException {
    return readLastDatabaseEventFromStream(
        new ObjectInputStream(new FileInputStream(dateDatabaseFilePath)));
  }

  public static SingleDateDatabaseEvent readLastDatabaseEventFromStream(
      final ObjectInputStream objectInputStream) throws IOException {
    boolean endReached = false;
    SingleDateDatabaseEvent singleDateDatabaseEvent = null;
    while (!endReached) {
      try {
        singleDateDatabaseEvent = (SingleDateDatabaseEvent) objectInputStream.readObject();
      } catch (final EOFException e) {
        endReached = true;
      } catch (final ClassNotFoundException e) {
        e.printStackTrace();
        throw new IllegalStateException(SINGLE_DATE_DATABASE_CORRUPTED_ERROR_MESSAGE);
      }
    }
    if (singleDateDatabaseEvent == null)
      throw new IllegalStateException(SINGLE_DATE_DATABASE_CORRUPTED_ERROR_MESSAGE);
    return singleDateDatabaseEvent;
  }

  public static ImmutableList<SingleDateDatabaseEvent> loadAllDatabaseEventFromStream(
      final ObjectInputStream objectInputStream) throws IOException {
    boolean endReached = false;
    final ImmutableList.Builder<SingleDateDatabaseEvent> singleDateDatabaseEvents =
        ImmutableList.builder();
    while (!endReached) {
      try {
        singleDateDatabaseEvents.add((SingleDateDatabaseEvent) objectInputStream.readObject());
      } catch (final EOFException e) {
        endReached = true;
      } catch (final ClassNotFoundException e) {
        e.printStackTrace();
        throw new IllegalStateException(SINGLE_DATE_DATABASE_CORRUPTED_ERROR_MESSAGE);
      }
    }
    return singleDateDatabaseEvents.build();
  }

  public static void writeDateDatabaseEventToStream(
      final SingleDateDatabaseEvent singleDateDatabaseEvent,
      final ObjectOutputStream outputStream) {
    try {
      outputStream.writeObject(singleDateDatabaseEvent);
      outputStream.flush();
    } catch (final IOException e) {
      throw new IllegalStateException(
          ERROR_OCCURRED_WHILE_WRITING_DATE_DATABASE_EVENT_TO_OBJECT_OUTPUT_STREAM + e.toString());
    }
  }

  @Override
  public Receive createReceive() {
    return initialBehaviour;
  }

  private Receive booked(final Booking booking) {
    return receiveBuilder()
        // Cancellation will write to disk right away, no need to transactional operation
        .match(
            SingleDateDatabaseCommand.CancelBooking.class,
            cancelBooking -> {
              final ActorRef sender = sender();
              if (booking.getId().equals(cancelBooking.bookingId)) {

                log.info("Cancelling : Booking id {}", cancelBooking.bookingId);

                // Inform read replica of state change
                readReplica.tell(cancelBooking, self());

                // Write to disk
                writeDateDatabaseEventToStream(SingleDateDatabaseEvent.noBooking(), outputStream);

                // Reply to requester
                sender.tell(
                    SingleDateDatabaseResponse.cancellationConfirmation(booking, date), self());
                getContext().become(available());
              } else {
                // Reply to requester
                sender.tell(
                    SingleDateDatabaseResponse.doesntQualifyForCancellationConfirmation(date),
                    self());
              }
            })
        .match(
            SingleDateDatabaseCommand.UpdateBooking.class,
            updateBooking -> {
              final ActorRef sender = sender();
              // if current booking matches the id of booking to update
              if (updateBooking.getBooking().getId().equals(booking.getId())) {

                // Reply to requester that we awaiting a transaction commit to persist this change
                sender.tell(
                    SingleDateDatabaseResponse.probatoryUpdateConfirmation(true, date), self());

                // if date of this database is within the reservation period
                if (updateBooking.getBooking().within(date)) {

                  // Override current Booking. Read Replica doesn't need to be informed because this
                  // is simply an override.
                  getContext()
                      .become(
                          transactionalBooked(Optional.of(booking), updateBooking.getBooking()),
                          false);
                } else {

                  // Delete current booking as it is no longer valid for this day.
                  getContext().become(transactionalAvailable(booking), false);
                }
              } else {
                if (updateBooking.getBooking().within(date)) {

                  // This date is booked by another user, inform sender
                  sender().tell(SingleDateDatabaseResponse.isBooked(date), self());
                } else {

                  // Reply to requester that this date doesn't qualify for an update (not within old
                  // and updated date ranges )
                  sender.tell(
                      SingleDateDatabaseResponse.doesntQualifyForUpdateConfirmation(date), self());
                }
              }
            })
        .match(
            SingleDateDatabaseCommand.Book.class,
            book -> {
              log.info("Date is already booked.");
              sender().tell(SingleDateDatabaseResponse.isBooked(date), self());
            })
        .match(
            SingleDateDatabaseCommand.RequestHistory.class,
            msg -> {
              final ActorRef sender = sender();
              sender.tell(
                  SingleDateDatabaseResponse.history(
                      loadAllDatabaseEventFromStream(inputStreamSupplier.get())),
                  self());
            })
        .match(
            SingleDateDatabaseCommand.GetAvailability.class,
            msg -> {
              final ActorRef sender = sender();
              sender.tell(SingleDateDatabaseResponse.isBooked(date), self());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  private Receive transactionalBooked(
      final Optional<Booking> maybePreviousBooking, final Booking booking) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseCommand.Commit.class,
            commit -> {
              // Commit in memory change to disk if commit message is intended for this date
              if (commit.getDate().equals(date)) {
                final ActorRef sender = sender();
                log.info("Persisting : {}", booking);

                // Writing booking to disk
                writeDateDatabaseEventToStream(
                    SingleDateDatabaseEvent.booked(booking), outputStream);

                // Reply to requester
                sender.tell(SingleDateDatabaseResponse.commitConfirmation(date), self());
                getContext().become(booked(booking), true);
              }
            })
        .match(
            SingleDateDatabaseCommand.Revert.class,
            revert -> {
              if (revert.getDate().equals(date)) {
                final ActorRef sender = sender();
                log.info("Reverting : Booking id {}", booking.getId());

                // If there is no previous booking that got overridden then we need to inform read
                // replica
                // that we are undoing that booking and that the date is available
                if (!maybePreviousBooking.isPresent()) {
                  readReplica.tell(SingleDateDatabaseCommand.cancel(booking.getId()), self());
                }

                // Reply to requester
                sender.tell(SingleDateDatabaseResponse.revertConfirmation(date), self());

                // Revert Actor state
                getContext().unbecome();
              }
            })
        .match(
            SingleDateDatabaseCommand.CancelBooking.class,
            cancelBooking -> {
              // Reply to requester
              sender()
                  .tell(
                      SingleDateDatabaseResponse.doesntQualifyForCancellationConfirmation(date),
                      self());
            })
        .match(
            SingleDateDatabaseCommand.UpdateBooking.class,
            updateBooking -> {
              if (updateBooking.getBooking().within(date)) {
                // This date is booked by another user, inform sender
                sender().tell(SingleDateDatabaseResponse.isBooked(date), self());
              } else {
                sender()
                    .tell(
                        SingleDateDatabaseResponse.doesntQualifyForUpdateConfirmation(date),
                        self());
              }
            })
        .match(
            SingleDateDatabaseCommand.Book.class,
            book -> {
              log.info("Date is already booked.");
              sender().tell(SingleDateDatabaseResponse.isBooked(date), self());
            })
        .match(
            SingleDateDatabaseCommand.RequestHistory.class,
            msg -> {
              final ActorRef sender = sender();

              final ImmutableList<SingleDateDatabaseEvent> history =
                  UtilityFunctions.addToImmutableList(
                      loadAllDatabaseEventFromStream(inputStreamSupplier.get()),
                      SingleDateDatabaseEvent.booked(booking));

              sender.tell(SingleDateDatabaseResponse.history(history), self());
            })
        .match(
            SingleDateDatabaseCommand.GetAvailability.class,
            msg -> {
              final ActorRef sender = sender();
              sender.tell(SingleDateDatabaseResponse.isBooked(date), self());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  private Receive available() {
    return receiveBuilder()
        .match(
            SingleDateDatabaseCommand.Book.class,
            book -> {
              final ActorRef sender = sender();
              log.info("Booking : {}", book.getBooking());

              // inform read replica of state change, date is now booked
              readReplica.tell(book, self());

              final SingleDateDatabaseResponse.BookingConfirmation bookingConfirmation =
                  SingleDateDatabaseResponse.bookingConfirmation(book.getBooking(), date);

              // Reply to requester that we awaiting a transaction commit to persist this change
              sender.tell(
                  SingleDateDatabaseResponse.probatoryBookingConfirmation(bookingConfirmation),
                  self());

              getContext().become(transactionalBooked(Optional.empty(), book.getBooking()), false);
            })
        .match(
            SingleDateDatabaseCommand.UpdateBooking.class,
            updateBooking -> {
              final ActorRef sender = sender();
              // if date of this database is within the reservation period book it
              if (updateBooking.getBooking().within(date)) {
                // inform read replica of state change
                readReplica.tell(
                    SingleDateDatabaseCommand.book(updateBooking.getBooking(), date), self());

                // Reply to requester that we awaiting a transaction commit to persist this change
                sender.tell(
                    SingleDateDatabaseResponse.probatoryUpdateConfirmation(false, date), self());

                getContext()
                    .become(
                        transactionalBooked(Optional.empty(), updateBooking.getBooking()), false);
              } else {
                // Reply to requester that this date doesn't qualify for an update
                sender.tell(
                    SingleDateDatabaseResponse.doesntQualifyForUpdateConfirmation(date), self());
              }
            })
        .match(
            SingleDateDatabaseCommand.CancelBooking.class,
            cancelBooking -> {
              sender()
                  .tell(
                      SingleDateDatabaseResponse.doesntQualifyForCancellationConfirmation(date),
                      self());
            })
        .match(
            SingleDateDatabaseCommand.RequestHistory.class,
            msg -> {
              final ActorRef sender = sender();
              log.info("Requested History of Booking.");
              sender.tell(
                  SingleDateDatabaseResponse.history(
                      loadAllDatabaseEventFromStream(inputStreamSupplier.get())),
                  self());
            })
        .match(
            SingleDateDatabaseCommand.GetAvailability.class,
            msg -> {
              final ActorRef sender = sender();
              log.info("Requested Availability.");
              sender.tell(SingleDateDatabaseResponse.isAvailable(date), self());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }

  private Receive transactionalAvailable(final Booking previousBooking) {
    return receiveBuilder()
        .match(
            SingleDateDatabaseCommand.Commit.class,
            commit -> {
              if (commit.getDate().equals(date)) {
                final ActorRef sender = sender();
                log.info("Persisting date as available");

                // Inform read replica of state change. We are informing the replica at this stage
                // because the transaction has ended and we want to let other user book.
                readReplica.tell(SingleDateDatabaseCommand.cancel(previousBooking.getId()), self());

                // Writing to disk
                writeDateDatabaseEventToStream(SingleDateDatabaseEvent.noBooking(), outputStream);

                // Reply to requester
                sender.tell(SingleDateDatabaseResponse.dateAvailableConfirmation(date), self());
                getContext().become(available(), true);
              }
            })
        .match(
            SingleDateDatabaseCommand.Revert.class,
            revert -> {
              if (revert.getDate().equals(date)) {
                final ActorRef sender = sender();
                log.info("Reverting to previous booking");

                // No need inform replica, as this date used to be booked. When in transactional
                // state we are also booked.
                // Read replica is already in the right state.

                // Reply to requester
                sender.tell(SingleDateDatabaseResponse.revertConfirmation(date), self());

                // Revert Actor state
                getContext().unbecome();
              }
            })
        .match(
            SingleDateDatabaseCommand.Book.class,
            book -> {

              // Date is available but in transaction mode. To prevent other users from booking this
              // will remain booked until the transaction is committed or reverted.
              sender().tell(SingleDateDatabaseResponse.isBooked(date), self());
            })
        .match(
            SingleDateDatabaseCommand.UpdateBooking.class,
            updateBooking -> {
              if (updateBooking.getBooking().within(date)) {
                // Date is available but in transaction mode. To prevent other users from booking
                // this will remain booked until the transaction is committed or reverted.
                sender().tell(SingleDateDatabaseResponse.isBooked(date), self());
              } else {
                sender()
                    .tell(
                        SingleDateDatabaseResponse.doesntQualifyForUpdateConfirmation(date),
                        self());
              }
            })
        .match(
            SingleDateDatabaseCommand.CancelBooking.class,
            cancelBooking -> {
              // We can't cancel while in transactional state
              sender()
                  .tell(
                      SingleDateDatabaseResponse.doesntQualifyForCancellationConfirmation(date),
                      self());
            })
        .match(
            SingleDateDatabaseCommand.RequestHistory.class,
            msg -> {
              final ActorRef sender = sender();

              final ImmutableList<SingleDateDatabaseEvent> history =
                  UtilityFunctions.addToImmutableList(
                      loadAllDatabaseEventFromStream(inputStreamSupplier.get()),
                      SingleDateDatabaseEvent.noBooking());

              sender.tell(SingleDateDatabaseResponse.history(history), self());
            })
        .match(
            SingleDateDatabaseCommand.GetAvailability.class,
            msg -> {
              final ActorRef sender = sender();
              log.info(
                  "Date is available but in transaction mode. "
                      + "To prevent other users from booking this will remain booked until the transaction is committed or reverted.");
              sender.tell(SingleDateDatabaseResponse.isBooked(date), self());
            })
        .matchAny(o -> log.info("received unknown message {}", o))
        .build();
  }
}
