package com.rimanware.volcanoisland.database.api;

import com.google.common.collect.ImmutableList;
import com.rimanware.volcanoisland.database.models.Booking;
import com.rimanware.volcanoisland.database.models.SingleDateDatabaseEvent;

import java.time.LocalDate;

public interface SingleDateDatabaseResponse {
  static History history(final ImmutableList<SingleDateDatabaseEvent> singleDateDatabaseEvents) {
    return History.create(singleDateDatabaseEvents);
  }

  static ProbatoryBookingConfirmation probatoryBookingConfirmation(
      final BookingConfirmation bookingConfirmation) {
    return ProbatoryBookingConfirmation.create(bookingConfirmation);
  }

  static ProbatoryUpdateConfirmation probatoryUpdateConfirmation(
      final Boolean overridesPreviousUpdate, final LocalDate date) {
    return ProbatoryUpdateConfirmation.create(overridesPreviousUpdate, date);
  }

  static BookingConfirmation bookingConfirmation(final Booking booking, final LocalDate date) {
    return BookingConfirmation.create(booking, date);
  }

  static DateAvailableConfirmation dateAvailableConfirmation(final LocalDate date) {
    return DateAvailableConfirmation.create(date);
  }

  static DoesntQualifyForUpdateConfirmation doesntQualifyForUpdateConfirmation(
      final LocalDate date) {
    return DoesntQualifyForUpdateConfirmation.create(date);
  }

  static DoesntQualifyForCancellationConfirmation doesntQualifyForCancellationConfirmation(
      final LocalDate date) {
    return DoesntQualifyForCancellationConfirmation.create(date);
  }

  static CancellationConfirmation cancellationConfirmation(
      final Booking booking, final LocalDate date) {
    return CancellationConfirmation.create(booking, date);
  }

  static IsAvailable isAvailable(final LocalDate date) {
    return IsAvailable.create(date);
  }

  static IsBooked isBooked(final LocalDate date) {
    return IsBooked.create(date);
  }

  static CommitConfirmation commitConfirmation(final LocalDate date) {
    return CommitConfirmation.create(date);
  }

  static RevertConfirmation revertConfirmation(final LocalDate date) {
    return RevertConfirmation.create(date);
  }

  final class History implements SingleDateDatabaseResponse {
    private final ImmutableList<SingleDateDatabaseEvent> events;

    private History(final ImmutableList<SingleDateDatabaseEvent> events) {
      this.events = events;
    }

    public static History create(final ImmutableList<SingleDateDatabaseEvent> events) {
      return new History(events);
    }

    @Override
    public String toString() {
      return "History{" + "events=" + events + '}';
    }

    public ImmutableList<SingleDateDatabaseEvent> getEvents() {
      return events;
    }
  }

  final class BookingConfirmation implements SingleDateDatabaseResponse {
    private final Booking booking;
    private final LocalDate date;

    public BookingConfirmation(final Booking booking, final LocalDate date) {
      this.booking = booking;
      this.date = date;
    }

    public static BookingConfirmation create(final Booking booking, final LocalDate date) {
      return new BookingConfirmation(booking, date);
    }

    @Override
    public String toString() {
      return "BookingConfirmation{" + "booking=" + booking + ", date=" + date + '}';
    }

    public Booking getBooking() {
      return booking;
    }

    public LocalDate getDate() {
      return date;
    }
  }

  final class DateAvailableConfirmation implements SingleDateDatabaseResponse {
    private final LocalDate date;

    public DateAvailableConfirmation(final LocalDate date) {
      this.date = date;
    }

    public static DateAvailableConfirmation create(final LocalDate date) {
      return new DateAvailableConfirmation(date);
    }

    @Override
    public String toString() {
      return "DateAvailableConfirmation{" + "date=" + date + '}';
    }

    public LocalDate getDate() {
      return date;
    }
  }

  final class ProbatoryBookingConfirmation implements SingleDateDatabaseResponse {
    private final BookingConfirmation bookingConfirmation;

    private ProbatoryBookingConfirmation(final BookingConfirmation bookingConfirmation) {
      this.bookingConfirmation = bookingConfirmation;
    }

    public static ProbatoryBookingConfirmation create(
        final BookingConfirmation bookingConfirmation) {
      return new ProbatoryBookingConfirmation(bookingConfirmation);
    }

    @Override
    public String toString() {
      return "ProbatoryBookingConfirmation{" + "bookingConfirmation=" + bookingConfirmation + '}';
    }

    public BookingConfirmation getBookingConfirmation() {
      return bookingConfirmation;
    }
  }

  final class ProbatoryUpdateConfirmation implements SingleDateDatabaseResponse {
    private final Boolean overridesPreviousUpdate;
    private final LocalDate date;

    private ProbatoryUpdateConfirmation(
        final Boolean overridesPreviousUpdate, final LocalDate date) {
      this.overridesPreviousUpdate = overridesPreviousUpdate;
      this.date = date;
    }

    public static ProbatoryUpdateConfirmation create(
        final Boolean overridesPreviousUpdate, final LocalDate date) {
      return new ProbatoryUpdateConfirmation(overridesPreviousUpdate, date);
    }

    @Override
    public String toString() {
      return "ProbatoryUpdateConfirmation{"
          + "overridesPreviousUpdate="
          + overridesPreviousUpdate
          + ", date="
          + date
          + '}';
    }

    public Boolean getOverridesPreviousUpdate() {
      return overridesPreviousUpdate;
    }

    public LocalDate getDate() {
      return date;
    }
  }

  final class DoesntQualifyForUpdateConfirmation implements SingleDateDatabaseResponse {
    private final LocalDate date;

    private DoesntQualifyForUpdateConfirmation(final LocalDate date) {
      this.date = date;
    }

    public static DoesntQualifyForUpdateConfirmation create(final LocalDate date) {
      return new DoesntQualifyForUpdateConfirmation(date);
    }

    @Override
    public String toString() {
      return "DoesntQualifyForUpdateConfirmation{" + "date=" + date + '}';
    }

    public LocalDate getDate() {
      return date;
    }
  }

  final class DoesntQualifyForCancellationConfirmation implements SingleDateDatabaseResponse {
    private final LocalDate date;

    private DoesntQualifyForCancellationConfirmation(final LocalDate date) {
      this.date = date;
    }

    public static DoesntQualifyForCancellationConfirmation create(final LocalDate date) {
      return new DoesntQualifyForCancellationConfirmation(date);
    }

    @Override
    public String toString() {
      return "DoesntQualifyForCancellationConfirmation{" + "date=" + date + '}';
    }

    public LocalDate getDate() {
      return date;
    }
  }

  final class CancellationConfirmation implements SingleDateDatabaseResponse {
    private final Booking booking;
    private final LocalDate date;

    public CancellationConfirmation(final Booking booking, final LocalDate date) {
      this.booking = booking;
      this.date = date;
    }

    public static CancellationConfirmation create(final Booking booking, final LocalDate date) {
      return new CancellationConfirmation(booking, date);
    }

    @Override
    public String toString() {
      return "CancellationConfirmation{" + "booking=" + booking + ", date=" + date + '}';
    }

    public Booking getBooking() {
      return booking;
    }

    public LocalDate getDate() {
      return date;
    }
  }

  final class IsAvailable implements SingleDateDatabaseResponse {
    private final LocalDate date;

    private IsAvailable(final LocalDate date) {
      this.date = date;
    }

    public static IsAvailable create(final LocalDate date) {
      return new IsAvailable(date);
    }

    @Override
    public String toString() {
      return "IsAvailable{" + "date=" + date + '}';
    }

    public LocalDate getDate() {
      return date;
    }
  }

  final class IsBooked implements SingleDateDatabaseResponse {
    private final LocalDate date;

    private IsBooked(final LocalDate date) {
      this.date = date;
    }

    public static IsBooked create(final LocalDate date) {
      return new IsBooked(date);
    }

    public LocalDate getDate() {
      return date;
    }

    @Override
    public String toString() {
      return "IsBooked{" + "date=" + date + '}';
    }
  }

  final class RevertConfirmation implements SingleDateDatabaseResponse {
    private final LocalDate date;

    private RevertConfirmation(final LocalDate date) {
      this.date = date;
    }

    public static RevertConfirmation create(final LocalDate date) {
      return new RevertConfirmation(date);
    }

    public LocalDate getDate() {
      return date;
    }

    @Override
    public String toString() {
      return "RevertConfirmation{" + "date=" + date + '}';
    }
  }

  final class CommitConfirmation implements SingleDateDatabaseResponse {
    private final LocalDate date;

    private CommitConfirmation(final LocalDate date) {
      this.date = date;
    }

    public static CommitConfirmation create(final LocalDate date) {
      return new CommitConfirmation(date);
    }

    @Override
    public String toString() {
      return "CommitConfirmation{" + "date=" + date + '}';
    }

    public LocalDate getDate() {
      return date;
    }
  }
}
