package com.rimanware.volcanoisland.database.api;

import java.time.LocalDate;

public interface RollingMonthDatabaseCommand {

  static RollingMonthDatabaseCommand start(final LocalDate date) {
    return Start.create(date);
  }

  static RollingMonthDatabaseCommand deactivate(final LocalDate date) {
    return Deactivate.INSTANCE;
  }

  static RollingMonthDatabaseCommand getQueryableDates() {
    return GetQueryableDates.INSTANCE;
  }

  enum GetQueryableDates implements RollingMonthDatabaseCommand {
    INSTANCE;

    GetQueryableDates() {}

    @Override
    public String toString() {
      return "GetQueryableDates{}";
    }
  }

  final class Start implements RollingMonthDatabaseCommand {
    private final LocalDate date;

    private Start(final LocalDate date) {
      this.date = date;
    }

    public static Start create(final LocalDate date) {
      return new Start(date);
    }

    @Override
    public String toString() {
      return "Start{" + "date=" + getDate() + '}';
    }

    public LocalDate getDate() {
      return date;
    }
  }

  enum Deactivate implements RollingMonthDatabaseCommand {
    INSTANCE;

    Deactivate() {}

    @Override
    public String toString() {
      return "Deactivate{}";
    }
  }
}
