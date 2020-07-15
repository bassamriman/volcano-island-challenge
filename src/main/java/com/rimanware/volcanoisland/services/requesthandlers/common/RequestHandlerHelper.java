package com.rimanware.volcanoisland.services.requesthandlers.common;

import com.google.common.collect.ImmutableList;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseResponse;
import com.rimanware.volcanoisland.errors.APIError;
import com.rimanware.volcanoisland.errors.APIErrorMessages;
import com.rimanware.volcanoisland.services.models.responses.DateError;
import com.rimanware.volcanoisland.services.models.responses.DateErrors;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.time.LocalDate;
import java.util.stream.Stream;

public final class RequestHandlerHelper {

  public static RequestHandlerResponse.Failure collectAllFailures(
      final ImmutableList<LocalDate> alreadyBookedFailedDates,
      final ImmutableList<RollingMonthDatabaseResponse.RequestedDateOutOfRange>
          outOfRangeFailedDates) {
    return RequestHandlerResponse.Failure.failed(
        DateErrors.create(
            Stream.concat(
                    outOfRangeFailedDates.stream()
                        .map(
                            outOfRangeFailedDate ->
                                DateError.create(
                                    outOfRangeFailedDate.getRequestedDate(),
                                    outOfRangeFailedDate.getReason().getApiError(),
                                    APIErrorMessages.ENGLISH)),
                    alreadyBookedFailedDates.stream()
                        .map(
                            alreadyBookedDate ->
                                DateError.create(
                                    alreadyBookedDate,
                                    APIError.AlreadyBooked,
                                    APIErrorMessages.ENGLISH)))
                .collect(ImmutableList.toImmutableList())));
  }
}
