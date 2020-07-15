package com.rimanware.volcanoisland.services.api;

import com.rimanware.volcanoisland.services.models.requests.BookingRequest;
import com.rimanware.volcanoisland.services.models.requests.UpdateBookingRequest;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.util.concurrent.CompletionStage;

public interface BookingService {
  CompletionStage<RequestHandlerResponse> createBooking(BookingRequest bookingRequest);

  CompletionStage<RequestHandlerResponse> updateBooking(UpdateBookingRequest updateBookingRequest);

  CompletionStage<RequestHandlerResponse> deleteBooking(String id);
}
