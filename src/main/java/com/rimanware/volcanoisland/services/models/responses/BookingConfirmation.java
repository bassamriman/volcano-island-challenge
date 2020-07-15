package com.rimanware.volcanoisland.services.models.responses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class BookingConfirmation {
  private final String bookingConfirmationId;

  @JsonCreator
  private BookingConfirmation(
      @JsonProperty("bookingConfirmationId") final String bookingConfirmationId) {
    this.bookingConfirmationId = bookingConfirmationId;
  }

  public static BookingConfirmation create(final String bookingConfirmationId) {
    return new BookingConfirmation(bookingConfirmationId);
  }

  public String getBookingConfirmationId() {
    return bookingConfirmationId;
  }
}
