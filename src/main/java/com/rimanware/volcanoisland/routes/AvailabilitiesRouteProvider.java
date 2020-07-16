package com.rimanware.volcanoisland.routes;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import com.rimanware.volcanoisland.business.BookingConstraintsImpl;
import com.rimanware.volcanoisland.common.DateValidator;
import com.rimanware.volcanoisland.errors.api.APIErrorMessages;
import com.rimanware.volcanoisland.services.api.AvailabilityService;
import com.rimanware.volcanoisland.services.models.requests.AvailabilitiesRequest;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public final class AvailabilitiesRouteProvider extends RouteProviderWithValidation {
  private static final String AVAILABILITIES = "availabilities";
  private final AvailabilityService availabilityService;
  private final APIErrorMessages apiErrorMessages;

  private AvailabilitiesRouteProvider(
      final AvailabilityService availabilityService, final APIErrorMessages apiErrorMessages) {
    this.availabilityService = availabilityService;
    this.apiErrorMessages = apiErrorMessages;
  }

  public static AvailabilitiesRouteProvider create(
      final AvailabilityService availabilityService, final APIErrorMessages apiErrorMessages) {
    return new AvailabilitiesRouteProvider(availabilityService, apiErrorMessages);
  }

  @Override
  public APIErrorMessages getAPIErrorMessages() {
    return apiErrorMessages;
  }

  @Override
  public Route getRoutes() {
    return pathPrefix(
        AVAILABILITIES,
        () ->
            route(
                // Create
                pathEnd(
                    () ->
                        route(
                            get(
                                () ->
                                    entity(
                                        Jackson.unmarshaller(AvailabilitiesRequest.DateRange.class),
                                        availabilitiesRequest ->
                                            getAvailabilitiesRequestRoute(
                                                availabilitiesRequest,
                                                availabilityService::getAvailabilities))),
                            get(
                                () ->
                                    onSuccess(
                                        availabilityService.getAvailabilities(),
                                        response ->
                                            handleBookingRequestResponse(
                                                response, StatusCodes.OK)))))));
  }

  private Route getAvailabilitiesRequestRoute(
      final AvailabilitiesRequest.DateRange availabilitiesRequest,
      final Function<AvailabilitiesRequest.DateRange, CompletionStage<RequestHandlerResponse>>
          requestHandler) {

    return validateThenHandleRequest(
        availabilitiesRequest,
        (request) ->
            DateValidator.availabilitiesDateRangeIsValid(
                request.getStartDate(), request.getEndDate(), BookingConstraintsImpl.INSTANCE),
        requestHandler,
        StatusCodes.OK);
  }
}
