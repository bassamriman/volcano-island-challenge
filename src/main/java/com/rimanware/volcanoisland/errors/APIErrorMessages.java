package com.rimanware.volcanoisland.errors;

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;

public enum APIErrorMessages {
  ENGLISH(
      ImmutableMap.copyOf(
          new HashMap<String, String>() {
            {
              put(APIError.AlreadyBooked.getKey(), "Date is already booked by another user.");
              put(APIError.AlreadyOccurred.getKey(), "Date already occurred.");
              put(
                  APIError.BookingIdNotFoundError.getKey(),
                  "No booking with the given ID was found.");
              put(
                  APIError.MaximumAheadOfArrivalError.getKey(),
                  "The campsite can be reserved up to 1 month in advance.");
              put(
                  APIError.MinimumAheadOfArrivalError.getKey(),
                  "The campsite can be reserved minimum 1 day(s) ahead of arrival.");
              put(
                  APIError.MaximumReservableDaysPerBookingError.getKey(),
                  "The campsite can be reserved for max 3 days.");
              put(
                  APIError.DepartureDateIsBeforeArrivalDateError.getKey(),
                  "The departure date can't be before arrival date.");
              put(
                  APIError.EndDateIsBeforeStartDateError.getKey(),
                  "The end date can't be before start date.");
            }
          }));

  private final ImmutableMap<String, String> apiErrorKeyToErrorMessage;

  APIErrorMessages(final ImmutableMap<String, String> apiErrorKeyToErrorMessage) {
    this.apiErrorKeyToErrorMessage = apiErrorKeyToErrorMessage;
  }

  public String getErrorMessage(final APIError apiError) {
    return this.apiErrorKeyToErrorMessage.getOrDefault(
        apiError.getKey(), "Oups. Something went wrong. Try again.");
  }
}
