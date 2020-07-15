package com.rimanware.volcanoisland.services.requesthandlers.api;

import akka.http.javadsl.model.StatusCode;
import akka.http.scaladsl.model.StatusCodes;
import com.rimanware.volcanoisland.errors.APIError;
import com.rimanware.volcanoisland.errors.APIErrorMessages;
import com.rimanware.volcanoisland.services.models.responses.Availabilities;
import com.rimanware.volcanoisland.services.models.responses.BookingConfirmation;
import com.rimanware.volcanoisland.services.models.responses.DateErrors;
import com.rimanware.volcanoisland.services.models.responses.SimpleError;

public interface RequestHandlerResponse {
  Object getResponse();

  interface Success extends RequestHandlerResponse {

    static BookingSuccess succeeded(final BookingConfirmation bookingConfirmation) {
      return BookingSuccess.create(bookingConfirmation);
    }

    static AvailabilitiesSuccess succeeded(final Availabilities availabilities) {
      return AvailabilitiesSuccess.create(availabilities);
    }

    final class BookingSuccess implements Success {

      private final BookingConfirmation bookingConfirmation;

      private BookingSuccess(final BookingConfirmation bookingConfirmation) {
        this.bookingConfirmation = bookingConfirmation;
      }

      public static BookingSuccess create(final BookingConfirmation bookingConfirmation) {
        return new BookingSuccess(bookingConfirmation);
      }

      @Override
      public String toString() {
        return "Success.Booking{" + "bookingConfirmation=" + bookingConfirmation + '}';
      }

      @Override
      public Object getResponse() {
        return bookingConfirmation;
      }
    }

    final class AvailabilitiesSuccess implements Success {
      private final Availabilities availabilities;

      private AvailabilitiesSuccess(final Availabilities availabilities) {
        this.availabilities = availabilities;
      }

      public static AvailabilitiesSuccess create(final Availabilities availabilities) {
        return new AvailabilitiesSuccess(availabilities);
      }

      @Override
      public Object getResponse() {
        return getAvailabilities();
      }

      @Override
      public String toString() {
        return "AvailabilitiesSuccess{" + "availabilities=" + availabilities + '}';
      }

      public Availabilities getAvailabilities() {
        return availabilities;
      }
    }
  }

  interface Failure extends RequestHandlerResponse {
    static Failure failed(final DateErrors dateErrors) {
      return MultipleDateFailures.create(dateErrors);
    }

    static SingleFailure failed(final APIError error, final APIErrorMessages errorMessages) {
      return SingleFailure.create(error, errorMessages);
    }

    @Override
    Object getResponse();

    StatusCode getHttpStatusCode();

    final class MultipleDateFailures implements Failure {
      private final DateErrors dateErrors;

      private MultipleDateFailures(final DateErrors dateErrors) {
        this.dateErrors = dateErrors;
      }

      public static MultipleDateFailures create(final DateErrors dateErrors) {
        return new MultipleDateFailures(dateErrors);
      }

      @Override
      public Object getResponse() {
        return dateErrors;
      }

      @Override
      public StatusCode getHttpStatusCode() {
        return StatusCodes.BadRequest();
      }

      @Override
      public String toString() {
        return "Failures{" + "dateErrors=" + dateErrors + '}';
      }
    }

    final class SingleFailure implements Failure {
      private final SimpleError error;
      private final StatusCode httpStatusCode;

      private SingleFailure(final SimpleError error, final StatusCode httpStatusCode) {
        this.error = error;
        this.httpStatusCode = httpStatusCode;
      }

      public static SingleFailure create(final SimpleError error, final StatusCode httpStatusCode) {
        return new SingleFailure(error, httpStatusCode);
      }

      public static SingleFailure create(
          final APIError error, final APIErrorMessages errorMessages) {
        return new SingleFailure(
            SimpleError.create(error, errorMessages), error.getHttpStatusCode());
      }

      @Override
      public Object getResponse() {
        return error;
      }

      @Override
      public StatusCode getHttpStatusCode() {
        return httpStatusCode;
      }

      @Override
      public String toString() {
        return "SingleFailure{" + "error=" + error + ", httpStatusCode=" + httpStatusCode + '}';
      }
    }
  }
}
