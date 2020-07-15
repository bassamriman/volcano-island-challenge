package com.rimanware.volcanoisland.routes;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import com.rimanware.volcanoisland.common.BookingConstraints;
import com.rimanware.volcanoisland.common.DateValidator;
import com.rimanware.volcanoisland.errors.APIErrorMessages;
import com.rimanware.volcanoisland.services.api.AvailabilityService;
import com.rimanware.volcanoisland.services.models.requests.AvailabilitiesRequest;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public final class AvailabilitiesRouteProvider extends RouteProviderWithValidation {
  private final AvailabilityService availabilityService;
  private final BookingConstraints bookingConstraints;
  private final APIErrorMessages apiErrorMessages;

  private AvailabilitiesRouteProvider(
      final AvailabilityService availabilityService,
      final BookingConstraints bookingConstraints,
      final APIErrorMessages apiErrorMessages) {
    this.availabilityService = availabilityService;
    this.bookingConstraints = bookingConstraints;
    this.apiErrorMessages = apiErrorMessages;
  }

  public static AvailabilitiesRouteProvider create(
      final AvailabilityService availabilityService,
      final BookingConstraints bookingConstraints,
      final APIErrorMessages apiErrorMessages) {
    return new AvailabilitiesRouteProvider(
        availabilityService, bookingConstraints, apiErrorMessages);
  }

  @Override
  public APIErrorMessages getAPIErrorMessages() {
    return apiErrorMessages;
  }

  @Override
  public Route getRoutes() {
    return pathPrefix(
        "availabilities",
        () ->
            route(
                // Create
                pathEnd(
                    () ->
                        get(
                            () ->
                                entity(
                                    Jackson.unmarshaller(AvailabilitiesRequest.class),
                                    availabilitiesRequest ->
                                        getAvailabilitiesRequestRoute(
                                            availabilitiesRequest,
                                            availabilityService::getAvailabilities))))));
  }

  private Route getAvailabilitiesRequestRoute(
      final AvailabilitiesRequest availabilitiesRequest,
      final Function<AvailabilitiesRequest, CompletionStage<RequestHandlerResponse>>
          requestHandler) {

    return validateThenHandleRequest(
        availabilitiesRequest,
        (request) ->
            DateValidator.availabilitiesDateRangeIsValid(
                request.getStartDate(), request.getEndDate(), BookingConstraints.INSTANCE),
        requestHandler,
        StatusCodes.OK);
  }
}
