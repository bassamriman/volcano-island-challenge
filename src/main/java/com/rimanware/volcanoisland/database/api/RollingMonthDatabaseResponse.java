package com.rimanware.volcanoisland.database.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.rimanware.volcanoisland.common.DateValidator;

import java.time.LocalDate;

public interface RollingMonthDatabaseResponse {
  static RequestedDateOutOfRange outOfRange(
      final LocalDate requestedDate, final DateValidator.Invalid.Reason reason) {
    return RequestedDateOutOfRange.create(requestedDate, reason);
  }

  static RequestedDatesOutOfRange outOfRange(
      final ImmutableList<RequestedDateOutOfRange> requestedDatesOutOfRange) {
    return RequestedDatesOutOfRange.create(requestedDatesOutOfRange);
  }

  static QueryableDates queryableDates(final ImmutableSet<String> queryableDates) {
    return QueryableDates.create(queryableDates);
  }

  final class RequestedDateOutOfRange implements RollingMonthDatabaseResponse {
    private final LocalDate requestedDate;
    private final DateValidator.Invalid.Reason reason;

    private RequestedDateOutOfRange(
        final LocalDate requestedDate, final DateValidator.Invalid.Reason reason) {
      this.requestedDate = requestedDate;
      this.reason = reason;
    }

    public static RequestedDateOutOfRange create(
        final LocalDate requestedDate, final DateValidator.Invalid.Reason reason) {
      return new RequestedDateOutOfRange(requestedDate, reason);
    }

    public LocalDate getRequestedDate() {
      return requestedDate;
    }

    public DateValidator.Invalid.Reason getReason() {
      return reason;
    }

    @Override
    public String toString() {
      return "RequestedDateOutOfRange{"
          + "requestedDate="
          + requestedDate
          + ", reason="
          + reason
          + '}';
    }
  }

  final class RequestedDatesOutOfRange implements RollingMonthDatabaseResponse {
    private final ImmutableList<RequestedDateOutOfRange> requestedDatesOutOfRange;

    private RequestedDatesOutOfRange(
        final ImmutableList<RequestedDateOutOfRange> requestedDatesOutOfRange) {
      this.requestedDatesOutOfRange = requestedDatesOutOfRange;
    }

    public static RollingMonthDatabaseResponse.RequestedDatesOutOfRange create(
        final ImmutableList<RequestedDateOutOfRange> requestedDatesOutOfRange) {
      return new RequestedDatesOutOfRange(requestedDatesOutOfRange);
    }

    @Override
    public String toString() {
      return "RequestedDatesOutOfRange{"
          + "requestedDatesOutOfRange="
          + requestedDatesOutOfRange
          + '}';
    }

    public ImmutableList<RequestedDateOutOfRange> getRequestedDatesOutOfRange() {
      return requestedDatesOutOfRange;
    }
  }

  final class QueryableDates implements RollingMonthDatabaseResponse {
    private final ImmutableSet<String> queryableDates;

    private QueryableDates(final ImmutableSet<String> queryableDates) {
      this.queryableDates = queryableDates;
    }

    public static QueryableDates create(final ImmutableSet<String> queryableDates) {
      return new QueryableDates(queryableDates);
    }

    @Override
    public String toString() {
      return "QueryableDates{" + "queryableDates=" + queryableDates + '}';
    }

    public ImmutableSet<String> getQueryableDates() {
      return queryableDates;
    }
  }
}
