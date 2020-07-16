package com.rimanware.volcanoisland.business;

import com.google.common.collect.ImmutableList;
import com.rimanware.volcanoisland.business.api.BookingConstraints;

import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.DAYS;

public class BookingConstraintsImpl implements BookingConstraints {
  public static final BookingConstraints INSTANCE = new BookingConstraintsImpl(1, 30, 3);

  private final int minimumAllowedDaysToBookAheadOfArrivalDate;
  private final int maximumAllowedDaysToBookAheadOfArrivalDate;
  private final int maximumReservableDaysPerBooking;

  private BookingConstraintsImpl(
      final int minimumAllowedDaysToBookAheadOfArrivalDate,
      final int maximumAllowedDaysToBookAheadOfArrivalDate,
      final int maximumReservableDaysPerBooking) {
    this.minimumAllowedDaysToBookAheadOfArrivalDate = minimumAllowedDaysToBookAheadOfArrivalDate;
    this.maximumAllowedDaysToBookAheadOfArrivalDate = maximumAllowedDaysToBookAheadOfArrivalDate;
    this.maximumReservableDaysPerBooking = maximumReservableDaysPerBooking;
  }

  @Override
  public LocalDate startDateOfReservationWindowGivenCurrentDate(final LocalDate currentDate) {
    return currentDate.plusDays(1 + this.minimumAllowedDaysToBookAheadOfArrivalDate);
  }

  @Override
  public LocalDate endDateOfReservationWindowGivenCurrentDate(final LocalDate currentDate) {
    return currentDate.plusDays(1 + this.maximumAllowedDaysToBookAheadOfArrivalDate);
  }

  @Override
  public Boolean withinMaximumReservableDaysPerBooking(final LocalDate start, final LocalDate end) {
    final long diffDays = DAYS.between(start, end.plusDays(1));
    return diffDays <= maximumReservableDaysPerBooking;
  }

  @Override
  public Boolean endDateIsBeforeStartDate(final LocalDate start, final LocalDate end) {
    return end.isBefore(start);
  }

  @Override
  public Boolean alreadyOccurred(final LocalDate dateToBook, final LocalDate currentDate) {
    return dateToBook.isBefore(currentDate) || dateToBook.equals(currentDate);
  }

  @Override
  public Boolean withinMinimumAllowedDaysAheadOfArrival(
      final LocalDate dateToBook, final LocalDate currentDate) {
    return dateToBook.isAfter(currentDate)
        && dateToBook.isBefore(startDateOfReservationWindowGivenCurrentDate(currentDate));
  }

  @Override
  public Boolean pastMaximumAllowedDaysAheadOfArrival(
      final LocalDate dateToBook, final LocalDate currentDate) {
    final LocalDate endDateOfReservationWindow =
        endDateOfReservationWindowGivenCurrentDate(currentDate);
    return dateToBook.isAfter(endDateOfReservationWindow);
  }

  @Override
  public ImmutableList<LocalDate> generateAllReservableDays(final LocalDate currentDate) {
    return ImmutableList.copyOf(
        Stream.iterate(
                startDateOfReservationWindowGivenCurrentDate(currentDate), d -> d.plusDays(1))
            .limit(maximumAllowedDaysToBookAheadOfArrivalDate)
            .collect(Collectors.toList()));
  }

  @Override
  public int getMinimumAllowedDaysToBookAheadOfArrivalDate() {
    return minimumAllowedDaysToBookAheadOfArrivalDate;
  }

  @Override
  public int getMaximumAllowedDaysToBookAheadOfArrivalDate() {
    return maximumAllowedDaysToBookAheadOfArrivalDate;
  }

  @Override
  public int getMaximumReservableDaysPerBooking() {
    return maximumReservableDaysPerBooking;
  }
}
