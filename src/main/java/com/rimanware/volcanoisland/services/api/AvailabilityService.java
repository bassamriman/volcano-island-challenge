package com.rimanware.volcanoisland.services.api;

import com.rimanware.volcanoisland.services.models.requests.AvailabilitiesRequest;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.util.concurrent.CompletionStage;

public interface AvailabilityService {
  CompletionStage<RequestHandlerResponse> getAvailabilities(
      AvailabilitiesRequest availabilitiesRequest);
}
