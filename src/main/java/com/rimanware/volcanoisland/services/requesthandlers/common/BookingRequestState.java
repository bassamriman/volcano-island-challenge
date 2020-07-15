package com.rimanware.volcanoisland.services.requesthandlers.common;

import akka.actor.ActorRef;
import com.google.common.collect.ImmutableList;
import com.rimanware.volcanoisland.common.UtilityFunctions;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseResponse;
import com.rimanware.volcanoisland.database.models.Booking;

import java.time.LocalDate;

public final class BookingRequestState implements SenderProvider {
  private final ImmutableList<LocalDate> newlyBookedDates;
  private final ImmutableList<LocalDate> alreadyBookedDates;
  private final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange> outOfRangeDates;
  private final Boolean foundBookingToBeUpdated;
  private final Booking booking;
  private final ActorRef sender;

  private BookingRequestState(
      final ImmutableList<LocalDate> newlyBookedDates,
      final ImmutableList<LocalDate> alreadyBookedDates,
      final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange> outOfRangeDates,
      final Boolean foundBookingToBeUpdated,
      final Booking booking,
      final ActorRef sender) {
    this.newlyBookedDates = newlyBookedDates;
    this.alreadyBookedDates = alreadyBookedDates;
    this.outOfRangeDates = outOfRangeDates;
    this.foundBookingToBeUpdated = foundBookingToBeUpdated;
    this.booking = booking;
    this.sender = sender;
  }

  public static BookingRequestState create(
      final ImmutableList<LocalDate> newlyBookedDates,
      final ImmutableList<LocalDate> alreadyBookedDates,
      final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange> outOfRangeDates,
      final Boolean foundBookingToBeUpdated,
      final Booking booking,
      final ActorRef sender) {
    return new BookingRequestState(
        newlyBookedDates,
        alreadyBookedDates,
        outOfRangeDates,
        foundBookingToBeUpdated,
        booking,
        sender);
  }

  public static BookingRequestState empty(final Booking booking, final ActorRef sender) {
    return BookingRequestState.create(
        ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), false, booking, sender);
  }

  public BookingRequestState addNewlyBookedDates(
      final ImmutableList<LocalDate> newNewlyBookedDates) {
    return BookingRequestState.create(
        UtilityFunctions.combine(newlyBookedDates, newNewlyBookedDates),
        alreadyBookedDates,
        outOfRangeDates,
        foundBookingToBeUpdated,
        booking,
        sender);
  }

  public BookingRequestState addNewlyBookedDate(final LocalDate newNewlyBookedDates) {
    return addNewlyBookedDates(ImmutableList.of(newNewlyBookedDates));
  }

  public BookingRequestState addAlreadyBookedDates(
      final ImmutableList<LocalDate> newAlreadyBookedDates) {
    return BookingRequestState.create(
        newlyBookedDates,
        UtilityFunctions.combine(alreadyBookedDates, newAlreadyBookedDates),
        outOfRangeDates,
        foundBookingToBeUpdated,
        booking,
        sender);
  }

  public BookingRequestState addAlreadyBookedDate(final LocalDate newAlreadyBookedDate) {
    return addAlreadyBookedDates(ImmutableList.of(newAlreadyBookedDate));
  }

  public BookingRequestState addOutOfRangeDates(
      final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange>
          newOutOfRangeDates) {
    return BookingRequestState.create(
        newlyBookedDates,
        alreadyBookedDates,
        UtilityFunctions.combine(outOfRangeDates, newOutOfRangeDates),
        foundBookingToBeUpdated,
        booking,
        sender);
  }

  public BookingRequestState addOutOfRangeDate(
      final RollingMonthDatabaseResponse.RequestedDateOutOfRange newOutOfRangeDate) {
    return addOutOfRangeDates(ImmutableList.of(newOutOfRangeDate));
  }

  public BookingRequestState foundBookingToBeUpdated(final Boolean found) {
    return BookingRequestState.create(
        newlyBookedDates,
        alreadyBookedDates,
        outOfRangeDates,
        foundBookingToBeUpdated || found,
        booking,
        sender);
  }

  public ImmutableList<LocalDate> getNewlyBookedDates() {
    return newlyBookedDates;
  }

  public ImmutableList<LocalDate> getAlreadyBookedDates() {
    return alreadyBookedDates;
  }

  public ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange> getOutOfRangeDates() {
    return outOfRangeDates;
  }

  public Boolean getFoundBookingToBeUpdated() {
    return foundBookingToBeUpdated;
  }

  public Booking getBooking() {
    return booking;
  }

  @Override
  public ActorRef getSender() {
    return sender;
  }
}
