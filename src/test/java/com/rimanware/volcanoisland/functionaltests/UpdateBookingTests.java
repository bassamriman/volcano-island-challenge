package com.rimanware.volcanoisland.functionaltests;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import com.rimanware.volcanoisland.common.RoutesTester;
import com.rimanware.volcanoisland.errors.APIErrorImpl;
import com.rimanware.volcanoisland.services.models.responses.*;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class UpdateBookingTests extends RoutesTester {

  @Test
  public void updateBookingShouldSuccessGivenNoOverlapWithPreviousBooking() {
    // Create initial booking
    final LocalDate createArrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate createDepartureDate = createArrivalDate.plusDays(2);

    final BookingConfirmation bookingConfirmation =
        getBookingConfirmation(createArrivalDate, createDepartureDate);

    // Update booking
    final LocalDate updateArrivalDate = createArrivalDate.plusDays(10);
    final LocalDate updateDepartureDate = updateArrivalDate.plusDays(2);
    volcanoIslandApp
        .run(
            HttpRequest.PUT("/bookings/" + bookingConfirmation.getBookingConfirmationId())
                .withEntity(
                    MediaTypes.APPLICATION_JSON.toContentType(),
                    "{\n"
                        + "    \"fullName\":\"Bassam Riman\",\n"
                        + "    \"email\":\"bassam.riman@gmail.com\",\n"
                        + "    \"arrivalDate\":\""
                        + updateArrivalDate.format(dateFormatter)
                        + "\",\n"
                        + "    \"departureDate\":\""
                        + updateDepartureDate.format(dateFormatter)
                        + "\"\n"
                        + "}"))
        .assertStatusCode(StatusCodes.OK);

    final Availabilities updateRangeAvailabilities =
        getAvailabilities(updateArrivalDate, updateDepartureDate);

    Assert.assertTrue(
        "There should be no availabilities on updates range",
        updateRangeAvailabilities.getAvailabilities().isEmpty());

    final Availabilities createRangeAvailabilities =
        getAvailabilities(createArrivalDate, createDepartureDate);

    Assert.assertFalse(
        "Previous date of the booking should be available",
        createRangeAvailabilities.getAvailabilities().isEmpty());
  }

  @Test
  public void updateBookingShouldSuccessGivenAnOverlapWithPreviousBooking() {
    // Create initial booking
    final LocalDate createArrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(2);
    final LocalDate createDepartureDate = createArrivalDate;

    final BookingConfirmation bookingConfirmation =
        getBookingConfirmation(createArrivalDate, createDepartureDate);

    // Update booking
    final LocalDate updateArrivalDate = createArrivalDate.minusDays(1);
    final LocalDate updateDepartureDate = updateArrivalDate.plusDays(1);
    volcanoIslandApp
        .run(
            HttpRequest.PUT("/bookings/" + bookingConfirmation.getBookingConfirmationId())
                .withEntity(
                    MediaTypes.APPLICATION_JSON.toContentType(),
                    "{\n"
                        + "    \"fullName\":\"Bassam Riman\",\n"
                        + "    \"email\":\"bassam.riman@gmail.com\",\n"
                        + "    \"arrivalDate\":\""
                        + updateArrivalDate.format(dateFormatter)
                        + "\",\n"
                        + "    \"departureDate\":\""
                        + updateDepartureDate.format(dateFormatter)
                        + "\"\n"
                        + "}"))
        .assertStatusCode(StatusCodes.OK);

    final Availabilities updateRangeAvailabilities =
        getAvailabilities(updateArrivalDate, updateDepartureDate);

    Assert.assertTrue(
        "There should be no availabilities on updates range",
        updateRangeAvailabilities.getAvailabilities().isEmpty());
  }

  @Test
  public void updateBookingShouldFailGivenDateRangeDoesntMeetMaxBookingDaysConstraint() {
    final LocalDate arrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate departureDate = arrivalDate.plusDays(3);
    volcanoIslandApp
        .run(
            HttpRequest.PUT("/bookings/dummyid")
                .withEntity(
                    MediaTypes.APPLICATION_JSON.toContentType(),
                    "{\n"
                        + "    \"fullName\":\"Bassam Riman\",\n"
                        + "    \"email\":\"bassam.riman@gmail.com\",\n"
                        + "    \"arrivalDate\":\""
                        + arrivalDate.format(dateFormatter)
                        + "\",\n"
                        + "    \"departureDate\":\""
                        + departureDate.format(dateFormatter)
                        + "\"\n"
                        + "}"))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntityAs(
            Jackson.unmarshaller(SimpleError.class),
            SimpleError.create(
                APIErrorImpl.MaximumReservableDaysPerBookingError, apiErrorMessages));

    final Availabilities availabilities = getAvailabilities(arrivalDate, departureDate);

    Assert.assertEquals(
        "Dates withing the booking request should remain available",
        availabilities.getAvailabilities().size(),
        ChronoUnit.DAYS.between(arrivalDate, departureDate.plusDays(1)));
  }

  @Test
  public void updateBookingShouldFailGivenDepartureDateBeforeArrivalDate() {
    final LocalDate arrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(2);
    final LocalDate departureDate = arrivalDate.minusDays(1);
    volcanoIslandApp
        .run(
            HttpRequest.PUT("/bookings/dummyid")
                .withEntity(
                    MediaTypes.APPLICATION_JSON.toContentType(),
                    "{\n"
                        + "    \"fullName\":\"Bassam Riman\",\n"
                        + "    \"email\":\"bassam.riman@gmail.com\",\n"
                        + "    \"arrivalDate\":\""
                        + arrivalDate.format(dateFormatter)
                        + "\",\n"
                        + "    \"departureDate\":\""
                        + departureDate.format(dateFormatter)
                        + "\"\n"
                        + "}"))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntityAs(
            Jackson.unmarshaller(SimpleError.class),
            SimpleError.create(
                APIErrorImpl.DepartureDateIsBeforeArrivalDateError, apiErrorMessages));

    final Availabilities availabilities = getAvailabilities(departureDate, arrivalDate);

    Assert.assertEquals(
        "Dates in the booking request should remain available",
        2,
        availabilities.getAvailabilities().size());
  }

  @Test
  public void updateBookingShouldFailGivenBookingIdDoesntExist() {
    final LocalDate arrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate departureDate = arrivalDate;

    final SimpleError error =
        volcanoIslandApp
            .run(
                HttpRequest.PUT("/bookings/dummyid")
                    .withEntity(
                        MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\n"
                            + "    \"fullName\":\"Bassam Riman\",\n"
                            + "    \"email\":\"bassam.riman@gmail.com\",\n"
                            + "    \"arrivalDate\":\""
                            + arrivalDate.format(dateFormatter)
                            + "\",\n"
                            + "    \"departureDate\":\""
                            + departureDate.format(dateFormatter)
                            + "\"\n"
                            + "}"))
            .assertStatusCode(StatusCodes.NOT_FOUND)
            .entity(Jackson.unmarshaller(SimpleError.class));

    final SimpleError expectedError =
        SimpleError.create(APIErrorImpl.BookingIdNotFoundError, apiErrorMessages);

    Assert.assertEquals(
        "Should contain error informing user that the given booking id was not found.",
        error,
        expectedError);
  }

  @Test
  public void updateBookingShouldFailGivenDateRangeIsWithinTheMinimumAllowedDaysAheadOfArrival() {
    // Create initial booking
    final LocalDate createArrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate createDepartureDate = createArrivalDate;
    final BookingConfirmation bookingConfirmation =
        getBookingConfirmation(createArrivalDate, createDepartureDate);

    // Update booking
    final LocalDate updateArrivalDate = currentDate.plusDays(1);
    final LocalDate updateDepartureDate = currentDate.plusDays(1);

    final DateErrors dateErrors =
        volcanoIslandApp
            .run(
                HttpRequest.PUT("/bookings/" + bookingConfirmation.getBookingConfirmationId())
                    .withEntity(
                        MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\n"
                            + "    \"fullName\":\"Bassam Riman\",\n"
                            + "    \"email\":\"bassam.riman@gmail.com\",\n"
                            + "    \"arrivalDate\":\""
                            + updateArrivalDate.format(dateFormatter)
                            + "\",\n"
                            + "    \"departureDate\":\""
                            + updateDepartureDate.format(dateFormatter)
                            + "\"\n"
                            + "}"))
            .assertStatusCode(StatusCodes.BAD_REQUEST)
            .entity(Jackson.unmarshaller(DateErrors.class));

    final DateError expectedDateError =
        DateError.create(
            updateArrivalDate, APIErrorImpl.MinimumAheadOfArrivalError, apiErrorMessages);

    Assert.assertTrue(
        "Should contain error informing user that the date is within the minimum allowed days ahead of arrival constraint.",
        dateErrors.getDateErrors().size() == 1
            && dateErrors.getDateErrors().get(0).equals(expectedDateError));

    final Availabilities createRangeAvailabilities =
        getAvailabilities(createArrivalDate, createDepartureDate);

    Assert.assertTrue(
        "Previously booked dates should not have changed",
        createRangeAvailabilities.getAvailabilities().isEmpty());
  }

  @Test
  public void updateBookingShouldFailGivenArrivalDateHasAlreadyOccurred() {
    // Create initial booking
    final LocalDate createArrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate createDepartureDate = createArrivalDate;
    final BookingConfirmation bookingConfirmation =
        getBookingConfirmation(createArrivalDate, createDepartureDate);

    // Update booking
    final LocalDate updateArrivalDate = currentDate;
    final LocalDate updateDepartureDate = updateArrivalDate;

    final DateErrors dateErrors =
        volcanoIslandApp
            .run(
                HttpRequest.PUT("/bookings/" + bookingConfirmation.getBookingConfirmationId())
                    .withEntity(
                        MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\n"
                            + "    \"fullName\":\"Bassam Riman\",\n"
                            + "    \"email\":\"bassam.riman@gmail.com\",\n"
                            + "    \"arrivalDate\":\""
                            + updateArrivalDate.format(dateFormatter)
                            + "\",\n"
                            + "    \"departureDate\":\""
                            + updateDepartureDate.format(dateFormatter)
                            + "\"\n"
                            + "}"))
            .assertStatusCode(StatusCodes.BAD_REQUEST)
            .entity(Jackson.unmarshaller(DateErrors.class));

    final DateError expectedDateError =
        DateError.create(updateArrivalDate, APIErrorImpl.AlreadyOccurred, apiErrorMessages);

    Assert.assertTrue(
        "Should contain error informing user that the date has already occurred.",
        dateErrors.getDateErrors().size() == 1
            && dateErrors.getDateErrors().get(0).equals(expectedDateError));

    final Availabilities createRangeAvailabilities =
        getAvailabilities(createArrivalDate, createDepartureDate);

    Assert.assertTrue(
        "Previously booked dates should not have changed",
        createRangeAvailabilities.getAvailabilities().isEmpty());
  }

  @Test
  public void updateBookingShouldFailGivenArrivalDateIsPastMaximumDaysAheadOfBooking() {
    // Create initial booking
    final LocalDate createArrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate createDepartureDate = createArrivalDate;
    final BookingConfirmation bookingConfirmation =
        getBookingConfirmation(createArrivalDate, createDepartureDate);

    // Update booking
    final LocalDate updateArrivalDate =
        bookingConstraints.endDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate updateDepartureDate = updateArrivalDate;

    final DateErrors dateErrors =
        volcanoIslandApp
            .run(
                HttpRequest.PUT("/bookings/" + bookingConfirmation.getBookingConfirmationId())
                    .withEntity(
                        MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\n"
                            + "    \"fullName\":\"Bassam Riman\",\n"
                            + "    \"email\":\"bassam.riman@gmail.com\",\n"
                            + "    \"arrivalDate\":\""
                            + updateArrivalDate.format(dateFormatter)
                            + "\",\n"
                            + "    \"departureDate\":\""
                            + updateDepartureDate.format(dateFormatter)
                            + "\"\n"
                            + "}"))
            .assertStatusCode(StatusCodes.BAD_REQUEST)
            .entity(Jackson.unmarshaller(DateErrors.class));

    final DateError expectedDateError =
        DateError.create(
            updateDepartureDate, APIErrorImpl.MaximumAheadOfArrivalError, apiErrorMessages);

    Assert.assertTrue(
        "Should contain error informing user that the date is past the maximum allowed days ahead of arrival constraint.",
        dateErrors.getDateErrors().size() == 1
            && dateErrors.getDateErrors().get(0).equals(expectedDateError));

    final Availabilities createRangeAvailabilities =
        getAvailabilities(createArrivalDate, createDepartureDate);

    Assert.assertTrue(
        "Previously booked dates should not have changed",
        createRangeAvailabilities.getAvailabilities().isEmpty());
  }

  @Test
  public void updateBookingShouldFailGivenMultipleReasons() {
    final LocalDate firstBookingArrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate);
    final LocalDate firstBookingDepartureDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate);

    // Book date
    volcanoIslandApp
        .run(
            HttpRequest.POST("/bookings")
                .withEntity(
                    MediaTypes.APPLICATION_JSON.toContentType(),
                    "{\n"
                        + "    \"fullName\":\"Bassam Riman\",\n"
                        + "    \"email\":\"bassam.riman@gmail.com\",\n"
                        + "    \"arrivalDate\":\""
                        + firstBookingArrivalDate.format(dateFormatter)
                        + "\",\n"
                        + "    \"departureDate\":\""
                        + firstBookingDepartureDate.format(dateFormatter)
                        + "\"\n"
                        + "}"))
        .assertStatusCode(StatusCodes.CREATED);

    // Create initial booking
    final LocalDate secondBookingArrivalDate = firstBookingArrivalDate.plusDays(1);
    final LocalDate secondBookingDepartureDate = secondBookingArrivalDate;
    final BookingConfirmation bookingConfirmation =
        getBookingConfirmation(secondBookingArrivalDate, secondBookingDepartureDate);

    // Update booking
    final LocalDate updateArrivalDate = firstBookingArrivalDate.minusDays(2);
    final LocalDate updateDepartureDate = firstBookingArrivalDate;

    final DateErrors dateErrors =
        volcanoIslandApp
            .run(
                HttpRequest.PUT("/bookings/" + bookingConfirmation.getBookingConfirmationId())
                    .withEntity(
                        MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\n"
                            + "    \"fullName\":\"Bassam Riman\",\n"
                            + "    \"email\":\"bassam.riman@gmail.com\",\n"
                            + "    \"arrivalDate\":\""
                            + updateArrivalDate.format(dateFormatter)
                            + "\",\n"
                            + "    \"departureDate\":\""
                            + updateDepartureDate.format(dateFormatter)
                            + "\"\n"
                            + "}"))
            .assertStatusCode(StatusCodes.BAD_REQUEST)
            .entity(Jackson.unmarshaller(DateErrors.class));

    final DateError expectedDateErrorDueToDateBeingBooked =
        DateError.create(updateDepartureDate, APIErrorImpl.AlreadyBooked, apiErrorMessages);

    final DateError expectedDateErrorDueToNotMeetingMinimumDateAheadOfArrivalConstraint =
        DateError.create(
            updateArrivalDate.plusDays(1),
            APIErrorImpl.MinimumAheadOfArrivalError,
            apiErrorMessages);

    final DateError expectedDateErrorDueToDateAlreadyOccurred =
        DateError.create(updateArrivalDate, APIErrorImpl.AlreadyOccurred, apiErrorMessages);

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

    final Availabilities createRangeAvailabilities =
        getAvailabilities(secondBookingArrivalDate, secondBookingDepartureDate);

    Assert.assertTrue(
        "Previously booked dates should not have changed",
        createRangeAvailabilities.getAvailabilities().isEmpty());
  }
}
