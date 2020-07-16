package com.rimanware.volcanoisland.errors;

import com.google.common.collect.ImmutableMap;
import com.rimanware.volcanoisland.errors.api.APIError;
import com.rimanware.volcanoisland.errors.api.APIErrorMessages;

import java.util.HashMap;

public enum APIErrorMessagesImpl implements APIErrorMessages {
  ENGLISH(
      ImmutableMap.copyOf(
          new HashMap<String, String>() {
            {
              put(APIErrorImpl.AlreadyBooked.getKey(), "Date is already booked by another user.");
              put(APIErrorImpl.AlreadyOccurred.getKey(), "Date already occurred.");
              put(
                  APIErrorImpl.BookingIdNotFoundError.getKey(),
                  "No booking with the given ID was found.");
              put(
                  APIErrorImpl.MaximumAheadOfArrivalError.getKey(),
                  "The campsite can be reserved up to 1 month in advance.");
              put(
                  APIErrorImpl.MinimumAheadOfArrivalError.getKey(),
                  "The campsite can be reserved minimum 1 day(s) ahead of arrival.");
              put(
                  APIErrorImpl.MaximumReservableDaysPerBookingError.getKey(),
                  "The campsite can be reserved for max 3 days.");
              put(
                  APIErrorImpl.DepartureDateIsBeforeArrivalDateError.getKey(),
                  "The departure date can't be before arrival date.");
              put(
                  APIErrorImpl.EndDateIsBeforeStartDateError.getKey(),
                  "The end date can't be before start date.");
            }
          }));

  private final ImmutableMap<String, String> apiErrorKeyToErrorMessage;

  APIErrorMessagesImpl(final ImmutableMap<String, String> apiErrorKeyToErrorMessage) {
    this.apiErrorKeyToErrorMessage = apiErrorKeyToErrorMessage;
  }

  @Override
  public String getErrorMessage(final APIError apiError) {
    return this.apiErrorKeyToErrorMessage.getOrDefault(
        apiError.getKey(), "Oups. Something went wrong. Try again.");
  }
}
