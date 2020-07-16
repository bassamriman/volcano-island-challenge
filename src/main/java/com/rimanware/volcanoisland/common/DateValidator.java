package com.rimanware.volcanoisland.common;

import com.rimanware.volcanoisland.business.api.BookingConstraints;
import com.rimanware.volcanoisland.errors.APIErrorImpl;
import com.rimanware.volcanoisland.errors.api.APIError;

import java.time.LocalDate;

public final class DateValidator {

  // TODO: Use Visitor Pattern & Predicate with result
  public static DateValidation isInValidRange(
      final LocalDate dateToValidate,
      final LocalDate currentDate,
      final BookingConstraints bookingConstraints) {
    if (bookingConstraints.alreadyOccurred(dateToValidate, currentDate)) {
      return DateValidation.invalid(Invalid.Reason.AlreadyOccurred);
    } else if (bookingConstraints.withinMinimumAllowedDaysAheadOfArrival(
        dateToValidate, currentDate)) {
      return DateValidation.invalid(Invalid.Reason.WithinMinimumAllowedDaysAheadOfArrival);
    } else if (bookingConstraints.pastMaximumAllowedDaysAheadOfArrival(
        dateToValidate, currentDate)) {
      return DateValidation.invalid(Invalid.Reason.PastMaximumAllowedDaysAheadOfArrival);
    } else {
      return DateValidation.valid();
    }
  }

  public static DateValidation bookingDateRangeIsValid(
      final LocalDate startDate,
      final LocalDate endDate,
      final BookingConstraints bookingConstraints) {
    if (bookingConstraints.endDateIsBeforeStartDate(startDate, endDate)) {
      return DateValidation.invalid(Invalid.Reason.DepartureDateIsBeforeArrivalDate);
    } else if (!bookingConstraints.withinMaximumReservableDaysPerBooking(startDate, endDate)) {
      return DateValidation.invalid(Invalid.Reason.MaximumReservableDaysPerBooking);
    } else {
      return DateValidation.valid();
    }
  }

  public static DateValidation availabilitiesDateRangeIsValid(
      final LocalDate startDate,
      final LocalDate endDate,
      final BookingConstraints bookingConstraints) {
    if (bookingConstraints.endDateIsBeforeStartDate(startDate, endDate)) {
      return DateValidation.invalid(Invalid.Reason.EndDateIsBeforeStartDate);
    } else {
      return DateValidation.valid();
    }
  }

  public enum Valid implements DateValidation {
    INSTANCE;

    Valid() {}

    @Override
    public String toString() {
      return "DateValidation.Valid{}";
    }
  }

  public interface DateValidation {
    static DateValidation valid() {
      return Valid.INSTANCE;
    }

    static DateValidation invalid(final Invalid.Reason reason) {
      return Invalid.create(reason);
    }
  }

  public static final class Invalid implements DateValidation {
    private final Reason reason;

    private Invalid(final Reason reason) {
      this.reason = reason;
    }

    public static Invalid create(final Reason reason) {
      return new Invalid(reason);
    }

    public Reason getReason() {
      return reason;
    }

    @Override
    public String toString() {
      return "DateValidation.Invalid{" + "reason=" + reason + '}';
    }

    public enum Reason {
      AlreadyOccurred(APIErrorImpl.AlreadyOccurred),
      WithinMinimumAllowedDaysAheadOfArrival(APIErrorImpl.MinimumAheadOfArrivalError),
      PastMaximumAllowedDaysAheadOfArrival(APIErrorImpl.MaximumAheadOfArrivalError),
      MaximumReservableDaysPerBooking(APIErrorImpl.MaximumReservableDaysPerBookingError),
      EndDateIsBeforeStartDate(APIErrorImpl.EndDateIsBeforeStartDateError),
      DepartureDateIsBeforeArrivalDate(APIErrorImpl.DepartureDateIsBeforeArrivalDateError);

      private final APIError apiError;

      Reason(final APIError apiError) {
        this.apiError = apiError;
      }

      public APIError getApiError() {
        return apiError;
      }

      @Override
      public String toString() {
        return "RequestedDateOutOfRange.Reason{}";
      }
    }
  }
}
