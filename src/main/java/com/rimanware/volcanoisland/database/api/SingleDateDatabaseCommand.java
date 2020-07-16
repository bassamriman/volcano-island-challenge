package com.rimanware.volcanoisland.database.api;

import com.rimanware.volcanoisland.database.models.Booking;

import java.time.LocalDate;

public interface SingleDateDatabaseCommand {
  static CancelBooking cancel(final String bookingId) {
    return CancelBooking.create(bookingId);
  }

  static Book book(final Booking booking, final LocalDate date) {
    return Book.create(booking, date);
  }

  static UpdateBooking update(final Booking booking) {
    return UpdateBooking.create(booking);
  }

  static Commit commit(final LocalDate date) {
    return Commit.create(date);
  }

  static Revert revert(final LocalDate date) {
    return Revert.create(date);
  }

  static RequestHistory history() {
    return RequestHistory.INSTANCE;
  }

  static GetAvailability getAvailability(final LocalDate date) {
    return GetAvailability.create(date);
  }

  enum RequestHistory implements SingleDateDatabaseCommand {
    INSTANCE;

    RequestHistory() {}

    @Override
    public String toString() {
      return "RequestHistory{}";
    }
  }

  final class Book implements SingleDateDatabaseCommand {
    private final Booking booking;
    private final LocalDate date;

    private Book(final Booking booking, final LocalDate date) {
      this.booking = booking;
      this.date = date;
    }

    public static Book create(final Booking booking, final LocalDate date) {
      return new Book(booking, date);
    }

    @Override
    public String toString() {
      return "Book{" + "booking=" + booking + ", date=" + date + '}';
    }

    public Booking getBooking() {
      return booking;
    }

    public LocalDate getDate() {
      return date;
    }
  }

  final class UpdateBooking implements SingleDateDatabaseCommand {
    private final Booking booking;

    private UpdateBooking(final Booking booking) {
      this.booking = booking;
    }

    public static UpdateBooking create(final Booking booking) {
      return new UpdateBooking(booking);
    }

    @Override
    public String toString() {
      return "UpdateBooking{" + "booking=" + booking + '}';
    }

    public Booking getBooking() {
      return booking;
    }
  }

  final class CancelBooking implements SingleDateDatabaseCommand {
    public final String bookingId;

    private CancelBooking(final String bookingId) {
      this.bookingId = bookingId;
    }

    public static CancelBooking create(final String bookingId) {
      return new CancelBooking(bookingId);
    }

    @Override
    public String toString() {
      return "CancelBooking{" + "bookingId='" + bookingId + '\'' + '}';
    }
  }

  final class Commit implements SingleDateDatabaseCommand {
    private final LocalDate date;

    private Commit(final LocalDate date) {
      this.date = date;
    }

    public static Commit create(final LocalDate date) {
      return new Commit(date);
    }

    @Override
    public String toString() {
      return "Commit{" + "date=" + date + '}';
    }

    public LocalDate getDate() {
      return date;
    }
  }

  final class Revert implements SingleDateDatabaseCommand {
    private final LocalDate date;

    private Revert(final LocalDate date) {
      this.date = date;
    }

    public static Revert create(final LocalDate date) {
      return new Revert(date);
    }

    @Override
    public String toString() {
      return "Revert{" + "date=" + date + '}';
    }

    public LocalDate getDate() {
      return date;
    }
  }

  final class GetAvailability implements SingleDateDatabaseCommand {
    private final LocalDate date;

    private GetAvailability(final LocalDate date) {
      this.date = date;
    }

    public static GetAvailability create(final LocalDate date) {
      return new GetAvailability(date);
    }

    @Override
    public String toString() {
      return "GetAvailability{" + "date=" + date + '}';
    }

    public LocalDate getDate() {
      return date;
    }
  }
}
