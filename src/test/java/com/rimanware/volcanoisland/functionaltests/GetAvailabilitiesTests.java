package com.rimanware.volcanoisland.functionaltests;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import com.rimanware.volcanoisland.common.RoutesTester;
import com.rimanware.volcanoisland.errors.APIErrorImpl;
import com.rimanware.volcanoisland.services.models.responses.Availabilities;
import com.rimanware.volcanoisland.services.models.responses.SimpleError;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;

public final class GetAvailabilitiesTests extends RoutesTester {
  @Test
  public void getAvailabilityShouldFailGivenEndDateBeforeStartDate() {
    final LocalDate startDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(2);
    final LocalDate endDate = startDate.minusDays(1);

    volcanoIslandApp
        .run(getAvailabilitiesRequest(startDate, endDate))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntityAs(
            Jackson.unmarshaller(SimpleError.class),
            SimpleError.create(APIErrorImpl.EndDateIsBeforeStartDateError, apiErrorMessages));
  }

  @Test
  public void
      getAvailabilityShouldOnlyReturnDaysThatAreAvailableForBookingGivenDateRangeIsBiggerThenAllowedRange() {
    final LocalDate startDate = currentDate.minusDays(10);
    final LocalDate endDate = currentDate.plusDays(50);

    final Availabilities availabilities = getAvailabilities(startDate, endDate);
    Assert.assertEquals(
        "Date range within the booking request should remain available",
        availabilities.getAvailabilities().size(),
        bookingConstraints.getMaximumAllowedDaysToBookAheadOfArrivalDate());
  }

  @Test
  public void getAvailabilityWithEmptyPayloadShouldReturnAllAvailableDateOfTheMonth() {
    final Availabilities availabilities =
        volcanoIslandApp
            .run(HttpRequest.GET("/availabilities"))
            .assertStatusCode(StatusCodes.OK)
            .entity(Jackson.unmarshaller(Availabilities.class));

    Assert.assertEquals(
        "Date range within the booking request should remain available",
        availabilities.getAvailabilities().size(),
        bookingConstraints.getMaximumAllowedDaysToBookAheadOfArrivalDate());
  }
}
