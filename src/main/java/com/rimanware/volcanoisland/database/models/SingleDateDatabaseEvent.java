package com.rimanware.volcanoisland.database.models;

import java.io.Serializable;

public interface SingleDateDatabaseEvent extends Serializable {
  static Booked booked(final Booking booking) {
    return Booked.create(booking);
  }

  static NoBooking noBooking() {
    return NoBooking.INSTANCE;
  }

  enum NoBooking implements SingleDateDatabaseEvent {
    INSTANCE;

    NoBooking() {}

    @Override
    public String toString() {
      return "NoBooking{}";
    }
  }

  final class Booked implements SingleDateDatabaseEvent {
    private final Booking booking;

    private Booked(final Booking booking) {
      this.booking = booking;
    }

    public static Booked create(final Booking booking) {
      return new Booked(booking);
    }

    public Booking getBooking() {
      return booking;
    }

    @Override
    public String toString() {
      return "Booked{" + "booking=" + booking + '}';
    }
  }
}
