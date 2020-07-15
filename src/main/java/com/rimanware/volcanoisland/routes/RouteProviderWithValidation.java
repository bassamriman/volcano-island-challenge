package com.rimanware.volcanoisland.routes;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import com.rimanware.volcanoisland.common.DateValidator;
import com.rimanware.volcanoisland.errors.APIError;
import com.rimanware.volcanoisland.errors.APIErrorMessages;
import com.rimanware.volcanoisland.routes.api.RouteProvider;
import com.rimanware.volcanoisland.services.models.responses.SimpleError;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public abstract class RouteProviderWithValidation extends AllDirectives implements RouteProvider {

  public abstract APIErrorMessages getAPIErrorMessages();

  public abstract Route getRoutes();

  protected final <RequestType> Route validateThenHandleRequest(
      final RequestType request,
      final Function<RequestType, DateValidator.DateValidation> validator,
      final Function<RequestType, CompletionStage<RequestHandlerResponse>> requestHandler,
      final StatusCode successStatusCode) {

    final DateValidator.DateValidation validation = validator.apply(request);

    if (validation instanceof DateValidator.Valid) {
      final Function<RequestHandlerResponse, Route> route =
          (RequestHandlerResponse result) ->
              handleBookingRequestResponse(result, successStatusCode);

      return onSuccess(requestHandler.apply(request), route);
    } else {
      final DateValidator.Invalid invalidDateRange = (DateValidator.Invalid) validation;
      final APIError apiError = invalidDateRange.getReason().getApiError();
      final SimpleError error =
          SimpleError.create(invalidDateRange.getReason().getApiError(), getAPIErrorMessages());
      return complete(apiError.getHttpStatusCode(), error, Jackson.marshaller());
    }
  }

  protected final Route handleBookingRequestResponse(
      final RequestHandlerResponse result, final StatusCode successStatusCode) {
    if (result instanceof RequestHandlerResponse.Success) {
      final RequestHandlerResponse.Success success = (RequestHandlerResponse.Success) result;
      return complete(successStatusCode, success.getResponse(), Jackson.marshaller());
    } else if (result instanceof RequestHandlerResponse.Failure) {
      final RequestHandlerResponse.Failure failure = (RequestHandlerResponse.Failure) result;
      return complete(failure.getHttpStatusCode(), failure.getResponse(), Jackson.marshaller());
    } else {
      // Should never happen as there is only
      // RequestHandlerResponse.Failure and
      // RequestHandlerResponse.Success
      return complete(StatusCodes.INTERNAL_SERVER_ERROR, null, Jackson.marshaller());
    }
  }
}
