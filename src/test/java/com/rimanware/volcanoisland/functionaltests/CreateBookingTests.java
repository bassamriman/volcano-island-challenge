package com.rimanware.volcanoisland.functionaltests;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import com.rimanware.volcanoisland.common.RoutesTester;
import com.rimanware.volcanoisland.errors.APIError;
import com.rimanware.volcanoisland.services.models.responses.*;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class CreateBookingTests extends RoutesTester {

  @Test
  public void createBookingShouldReturnBookingConfirmationIdGivenBookingRequestIsValid() {
    final LocalDate arrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate departureDate = arrivalDate.plusDays(2);
    create(arrivalDate, departureDate)
        .assertStatusCode(StatusCodes.CREATED)
        .entity(Jackson.unmarshaller(BookingConfirmation.class));

    final Availabilities availabilities = getAvailabilities(arrivalDate, departureDate);

    Assert.assertTrue("The date should be booked", availabilities.getAvailabilities().isEmpty());
  }

  @Test
  public void createBookingShouldFailGivenDateRangeDoesntMeetMaxBookingDaysConstraint() {
    final LocalDate arrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate departureDate = arrivalDate.plusDays(3);
    create(arrivalDate, departureDate)
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntityAs(
            Jackson.unmarshaller(SimpleError.class),
            SimpleError.create(APIError.MaximumReservableDaysPerBookingError, apiErrorMessages));

    final Availabilities availabilities = getAvailabilities(arrivalDate, departureDate);

    Assert.assertEquals(
        "Dates withing the booking request should remain available",
        availabilities.getAvailabilities().size(),
        ChronoUnit.DAYS.between(arrivalDate, departureDate.plusDays(1)));
  }

  @Test
  public void createBookingShouldFailGivenDepartureDateBeforeArrivalDate() {
    final LocalDate arrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(2);
    final LocalDate departureDate = arrivalDate.minusDays(1);
    create(arrivalDate, departureDate)
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntityAs(
            Jackson.unmarshaller(SimpleError.class),
            SimpleError.create(APIError.DepartureDateIsBeforeArrivalDateError, apiErrorMessages));

    final Availabilities availabilities = getAvailabilities(departureDate, arrivalDate);

    Assert.assertEquals(
        "Dates in the booking request should remain available",
        2,
        availabilities.getAvailabilities().size());
  }

  @Test
  public void createBookingShouldFailGivenDateRangeIsWithinTheMinimumAllowedDaysAheadOfArrival() {
    final LocalDate arrivalDate = currentDate.plusDays(1);
    final LocalDate departureDate = currentDate.plusDays(1);

    final DateErrors dateErrors =
        create(arrivalDate, departureDate)
            .assertStatusCode(StatusCodes.BAD_REQUEST)
            .entity(Jackson.unmarshaller(DateErrors.class));

    final DateError expectedDateError =
        DateError.create(arrivalDate, APIError.MinimumAheadOfArrivalError, apiErrorMessages);

    Assert.assertTrue(
        "Should contain error informing user that the date is within the minimum allowed days ahead of arrival constraint.",
        dateErrors.getDateErrors().size() == 1
            && dateErrors.getDateErrors().get(0).equals(expectedDateError));
  }

  @Test
  public void createBookingShouldFailGivenArrivalDateHasAlreadyOccurred() {
    final LocalDate arrivalDate = currentDate;
    final LocalDate departureDate = arrivalDate;

    final DateErrors dateErrors =
        create(arrivalDate, departureDate)
            .assertStatusCode(StatusCodes.BAD_REQUEST)
            .entity(Jackson.unmarshaller(DateErrors.class));

    final DateError expectedDateError =
        DateError.create(arrivalDate, APIError.AlreadyOccurred, apiErrorMessages);

    Assert.assertTrue(
        "Should contain error informing user that the date has already occurred.",
        dateErrors.getDateErrors().size() == 1
            && dateErrors.getDateErrors().get(0).equals(expectedDateError));
  }

  @Test
  public void createBookingShouldFailGivenArrivalDateIsPastMaximumDaysAheadOfBooking() {
    final LocalDate arrivalDate =
        bookingConstraints.endDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate departureDate = arrivalDate;

    final DateErrors dateErrors =
        create(arrivalDate, departureDate)
            .assertStatusCode(StatusCodes.BAD_REQUEST)
            .entity(Jackson.unmarshaller(DateErrors.class));

    final DateError expectedDateError =
        DateError.create(arrivalDate, APIError.MaximumAheadOfArrivalError, apiErrorMessages);

    Assert.assertTrue(
        "Should contain error informing user that the date is past the maximum allowed days ahead of arrival constraint.",
        dateErrors.getDateErrors().size() == 1
            && dateErrors.getDateErrors().get(0).equals(expectedDateError));
  }

  @Test
  public void createBookingShouldFailGivenMultipleReasons() {
    final LocalDate firstBookingArrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate);
    final LocalDate firstBookingDepartureDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate);

    // Book date
    create(firstBookingArrivalDate, firstBookingDepartureDate)
        .assertStatusCode(StatusCodes.CREATED);

    final LocalDate secondBookingArrivalDate = firstBookingArrivalDate.minusDays(2);
    final LocalDate secondBookingDepartureDate = secondBookingArrivalDate.plusDays(2);

    final DateErrors dateErrors =
        create(secondBookingArrivalDate, secondBookingDepartureDate)
            .assertStatusCode(StatusCodes.BAD_REQUEST)
            .entity(Jackson.unmarshaller(DateErrors.class));

    final DateError expectedDateErrorDueToDateBeingBooked =
        DateError.create(secondBookingDepartureDate, APIError.AlreadyBooked, apiErrorMessages);

    final DateError expectedDateErrorDueToNotMeetingMinimumDateAheadOfArrivalConstraint =
        DateError.create(
            secondBookingArrivalDate.plusDays(1),
            APIError.MinimumAheadOfArrivalError,
            apiErrorMessages);

    final DateError expectedDateErrorDueToDateAlreadyOccurred =
        DateError.create(secondBookingArrivalDate, APIError.AlreadyOccurred, apiErrorMessages);

    Assert.assertEquals("Response should contain 3 errors", 3, dateErrors.getDateErrors().size());

    Assert.assertTrue(
        "Should contain error informing user that the date is already booked",
        dateErrors.getDateErrors().contains(expectedDateErrorDueToDateBeingBooked));

    Assert.assertTrue(
        "Should contain error informing user that the date is within the minimum allowed days ahead of arrival constraint.",
        dateErrors
            .getDateErrors()
            .contains(expectedDateErrorDueToNotMeetingMinimumDateAheadOfArrivalConstraint));

    Assert.assertTrue(
        "Should contain error informing user that the date has already occurred.",
        dateErrors.getDateErrors().contains(expectedDateErrorDueToDateAlreadyOccurred));
  }

  @Test
  public void createBookingShouldFailGivenDateIsAlreadyBooked() {
    final LocalDate arrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate departureDate = arrivalDate;
    create(arrivalDate, departureDate)
        .assertStatusCode(StatusCodes.CREATED)
        .entity(Jackson.unmarshaller(BookingConfirmation.class));

    final DateErrors dateErrors =
        create(arrivalDate, departureDate)
            .assertStatusCode(StatusCodes.BAD_REQUEST)
            .entity(Jackson.unmarshaller(DateErrors.class));

    final DateError expectedDateErrorDueToDateBeingBooked =
        DateError.create(arrivalDate, APIError.AlreadyBooked, apiErrorMessages);

    Assert.assertTrue(
        "Should contain error informing user the date is already booked",
        dateErrors.getDateErrors().contains(expectedDateErrorDueToDateBeingBooked));
  }
}
