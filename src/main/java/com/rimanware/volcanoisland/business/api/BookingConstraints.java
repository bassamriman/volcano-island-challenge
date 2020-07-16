package com.rimanware.volcanoisland.business.api;

import com.google.common.collect.ImmutableList;

import java.time.LocalDate;

public interface BookingConstraints {
  LocalDate startDateOfReservationWindowGivenCurrentDate(LocalDate currentDate);

  LocalDate endDateOfReservationWindowGivenCurrentDate(LocalDate currentDate);

  Boolean withinMaximumReservableDaysPerBooking(LocalDate start, LocalDate end);

  Boolean endDateIsBeforeStartDate(LocalDate start, LocalDate end);

  Boolean alreadyOccurred(LocalDate dateToBook, LocalDate currentDate);

  Boolean withinMinimumAllowedDaysAheadOfArrival(LocalDate dateToBook, LocalDate currentDate);

  Boolean pastMaximumAllowedDaysAheadOfArrival(LocalDate dateToBook, LocalDate currentDate);

  ImmutableList<LocalDate> generateAllReservableDays(LocalDate currentDate);

  int getMinimumAllowedDaysToBookAheadOfArrivalDate();

  int getMaximumAllowedDaysToBookAheadOfArrivalDate();

  int getMaximumReservableDaysPerBooking();
}
