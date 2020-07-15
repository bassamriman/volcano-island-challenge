package com.rimanware.volcanoisland.services.models.requests;

public final class UpdateBookingRequest {
  private final String id;
  private final BookingRequest bookingRequest;

  private UpdateBookingRequest(final String id, final BookingRequest bookingRequest) {
    this.id = id;
    this.bookingRequest = bookingRequest;
  }

  public static UpdateBookingRequest create(final String id, final BookingRequest bookingRequest) {
    return new UpdateBookingRequest(id, bookingRequest);
  }

  public String getId() {
    return id;
  }

  public BookingRequest getBookingRequest() {
    return bookingRequest;
  }

  @Override
  public String toString() {
    return "UpdateRequest{" + "id='" + id + '\'' + ", bookingRequest=" + bookingRequest + '}';
  }
}
