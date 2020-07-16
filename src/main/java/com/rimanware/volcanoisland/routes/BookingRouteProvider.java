package com.rimanware.volcanoisland.routes;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import com.rimanware.volcanoisland.business.api.BookingConstraints;
import com.rimanware.volcanoisland.common.DateValidator;
import com.rimanware.volcanoisland.errors.api.APIErrorMessages;
import com.rimanware.volcanoisland.services.api.BookingService;
import com.rimanware.volcanoisland.services.models.requests.BookingRequest;
import com.rimanware.volcanoisland.services.models.requests.UpdateBookingRequest;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public final class BookingRouteProvider extends RouteProviderWithValidation {
  private static final String BOOKINGS = "bookings";
  private final BookingService bookingService;
  private final BookingConstraints bookingConstraints;
  private final APIErrorMessages apiErrorMessages;

  private BookingRouteProvider(
      final BookingService bookingService,
      final BookingConstraints bookingConstraints,
      final APIErrorMessages apiErrorMessages) {
    this.bookingService = bookingService;
    this.bookingConstraints = bookingConstraints;
    this.apiErrorMessages = apiErrorMessages;
  }

  public static BookingRouteProvider create(
      final BookingService bookingService,
      final BookingConstraints bookingConstraints,
      final APIErrorMessages apiErrorMessages) {
    return new BookingRouteProvider(bookingService, bookingConstraints, apiErrorMessages);
  }

  @Override
  public APIErrorMessages getAPIErrorMessages() {
    return apiErrorMessages;
  }

  @Override
  public Route getRoutes() {
    return pathPrefix(
        BOOKINGS,
        () ->
            route(
                // Create
                pathEnd(
                    () ->
                        post(
                            () ->
                                entity(
                                    Jackson.unmarshaller(BookingRequest.class),
                                    bookingRequest ->
                                        getBookingRoute(
                                            bookingRequest,
                                            bookingService::createBooking,
                                            StatusCodes.CREATED)))),
                // Update & Delete
                path(
                    PathMatchers.segment(),
                    (String id) ->
                        route(
                            put(
                                () ->
                                    entity(
                                        Jackson.unmarshaller(BookingRequest.class),
                                        bookingRequest ->
                                            getBookingRoute(
                                                bookingRequest,
                                                (request ->
                                                    bookingService.updateBooking(
                                                        UpdateBookingRequest.create(id, request))),
                                                StatusCodes.OK))),
                            delete(
                                () ->
                                    onSuccess(
                                        bookingService.deleteBooking(id),
                                        response ->
                                            handleBookingRequestResponse(
                                                response, StatusCodes.OK)))))));
  }

  private Route getBookingRoute(
      final BookingRequest bookingRequest,
      final Function<BookingRequest, CompletionStage<RequestHandlerResponse>> requestHandler,
      final StatusCode successStatusCode) {

    return validateThenHandleRequest(
        bookingRequest,
        (request) ->
            DateValidator.bookingDateRangeIsValid(
                request.getArrivalDate(), request.getDepartureDate(), bookingConstraints),
        requestHandler,
        successStatusCode);
  }
}
