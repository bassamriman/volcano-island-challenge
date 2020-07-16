package com.rimanware.volcanoisland.functionaltests;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import com.rimanware.volcanoisland.common.RoutesTester;
import com.rimanware.volcanoisland.services.models.responses.Availabilities;
import com.rimanware.volcanoisland.services.models.responses.BookingConfirmation;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;

public final class DeleteBookingTests extends RoutesTester {

  @Test
  public void deleteBookingShouldSuccessGivenBookingExists() {
    // Create initial booking
    final LocalDate createArrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate createDepartureDate = createArrivalDate.plusDays(2);
    final BookingConfirmation bookingConfirmation =
        volcanoIslandApp
            .run(createRequest(createArrivalDate, createDepartureDate))
            .assertStatusCode(StatusCodes.CREATED)
            .entity(Jackson.unmarshaller(BookingConfirmation.class));

    // Delete booking
    volcanoIslandApp
        .run(HttpRequest.DELETE("/bookings/" + bookingConfirmation.getBookingConfirmationId()))
        .assertStatusCode(StatusCodes.OK);

    final Availabilities createRange =
        getAvailabilities(
            bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate),
            bookingConstraints.endDateOfReservationWindowGivenCurrentDate(currentDate));
    Assert.assertFalse(
        "Dates of the canceled booking should be available",
        createRange.getAvailabilities().isEmpty());
  }

  @Test
  public void updateBookingShouldFailGivenBookingIdDoesntExist() {
    volcanoIslandApp
        .run(HttpRequest.DELETE("/bookings/dummyid"))
        .assertStatusCode(StatusCodes.NOT_FOUND);
  }
}
