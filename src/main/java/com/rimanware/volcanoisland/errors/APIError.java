package com.rimanware.volcanoisland.errors;

import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;

public enum APIError {
  AlreadyBooked("ALREADY_BOOKED", StatusCodes.BAD_REQUEST),
  AlreadyOccurred("ALREADY_OCCURRED", StatusCodes.BAD_REQUEST),
  MinimumAheadOfArrivalError("MINIMUM_AHEAD_OF_ARRIVAL_ERROR", StatusCodes.BAD_REQUEST),
  MaximumAheadOfArrivalError("MAXIMUM_AHEAD_OF_ARRIVAL_ERROR", StatusCodes.BAD_REQUEST),
  MaximumReservableDaysPerBookingError(
      "MAXIMUM_RESERVABLE_DAYS_PER_BOOKING", StatusCodes.BAD_REQUEST),
  BookingIdNotFoundError("BOOKING_ID_NOT_FOUND", StatusCodes.NOT_FOUND),
  DepartureDateIsBeforeArrivalDateError(
      "DEPARTURE_DATE_IS_BEFORE_ARRIVAL_DATE", StatusCodes.BAD_REQUEST),
  EndDateIsBeforeStartDateError("END_DATE_IS_BEFORE_START_DATE", StatusCodes.BAD_REQUEST);

  private final String key;
  private final StatusCode httpStatusCode;

  APIError(final String key, final StatusCode httpStatusCode) {
    this.key = key;
    this.httpStatusCode = httpStatusCode;
  }

  public String getKey() {
    return key;
  }

  public StatusCode getHttpStatusCode() {
    return httpStatusCode;
  }

  @Override
  public String toString() {
    return "APIError{" + "key='" + key + '\'' + ", httpStatusCode=" + httpStatusCode + '}';
  }
}
