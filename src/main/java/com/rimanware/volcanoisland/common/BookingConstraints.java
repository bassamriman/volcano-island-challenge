package com.rimanware.volcanoisland.common;

import com.google.common.collect.ImmutableList;

import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.DAYS;

public class BookingConstraints {
  public static BookingConstraints INSTANCE = new BookingConstraints(1, 30, 3);

  private final int minimumAllowedDaysToBookAheadOfArrivalDate;
  private final int maximumAllowedDaysToBookAheadOfArrivalDate;
  private final int maximumReservableDaysPerBooking;

  private BookingConstraints(
      final int minimumAllowedDaysToBookAheadOfArrivalDate,
      final int maximumAllowedDaysToBookAheadOfArrivalDate,
      final int maximumReservableDaysPerBooking) {
    this.minimumAllowedDaysToBookAheadOfArrivalDate = minimumAllowedDaysToBookAheadOfArrivalDate;
    this.maximumAllowedDaysToBookAheadOfArrivalDate = maximumAllowedDaysToBookAheadOfArrivalDate;
    this.maximumReservableDaysPerBooking = maximumReservableDaysPerBooking;
  }

  public LocalDate startDateOfReservationWindowGivenCurrentDate(final LocalDate currentDate) {
    return currentDate.plusDays(1 + this.minimumAllowedDaysToBookAheadOfArrivalDate);
  }

  public LocalDate endDateOfReservationWindowGivenCurrentDate(final LocalDate currentDate) {
    return currentDate.plusDays(1 + this.maximumAllowedDaysToBookAheadOfArrivalDate);
  }

  public Boolean withinMaximumReservableDaysPerBooking(final LocalDate start, final LocalDate end) {
    final long diffDays = DAYS.between(start, end.plusDays(1));
    return diffDays <= maximumReservableDaysPerBooking;
  }

  public Boolean endDateIsBeforeStartDate(final LocalDate start, final LocalDate end) {
    return end.isBefore(start);
  }

  public Boolean alreadyOccurred(final LocalDate dateToBook, final LocalDate currentDate) {
    return dateToBook.isBefore(currentDate) || dateToBook.equals(currentDate);
  }

  public Boolean withinMinimumAllowedDaysAheadOfArrival(
      final LocalDate dateToBook, final LocalDate currentDate) {
    return dateToBook.isAfter(currentDate)
        && dateToBook.isBefore(startDateOfReservationWindowGivenCurrentDate(currentDate));
  }

  public Boolean pastMaximumAllowedDaysAheadOfArrival(
      final LocalDate dateToBook, final LocalDate currentDate) {
    final LocalDate endDateOfReservationWindow =
        endDateOfReservationWindowGivenCurrentDate(currentDate);
    return dateToBook.isAfter(endDateOfReservationWindow);
  }

  public ImmutableList<LocalDate> generateAllReservableDays(final LocalDate currentDate) {
    return ImmutableList.copyOf(
        Stream.iterate(
                startDateOfReservationWindowGivenCurrentDate(currentDate), d -> d.plusDays(1))
            .limit(maximumAllowedDaysToBookAheadOfArrivalDate)
            .collect(Collectors.toList()));
  }

  public int getMinimumAllowedDaysToBookAheadOfArrivalDate() {
    return minimumAllowedDaysToBookAheadOfArrivalDate;
  }

  public int getMaximumAllowedDaysToBookAheadOfArrivalDate() {
    return maximumAllowedDaysToBookAheadOfArrivalDate;
  }

  public int getMaximumReservableDaysPerBooking() {
    return maximumReservableDaysPerBooking;
  }
}
