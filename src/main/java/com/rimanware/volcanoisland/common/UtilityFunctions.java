package com.rimanware.volcanoisland.common;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.stream.Stream;

public final class UtilityFunctions {

  public static <T> ImmutableList<T> addToImmutableList(
      final ImmutableList<T> immutableList, final T newEntry) {
    return combine(immutableList, ImmutableList.of(newEntry));
  }

  public static <T> ImmutableList<T> combine(
      final ImmutableList<T> immutableList1, final ImmutableList<T> immutableList2) {
    return new LinkedList<T>() {
      {
        addAll(immutableList1);
        addAll(immutableList2);
      }
    }.stream().collect(ImmutableList.toImmutableList());
  }

  public static <T> ImmutableSet<T> addToImmutableSet(
      final ImmutableSet<T> immutableSet, final T newEntry) {
    return combine(immutableSet, ImmutableSet.of(newEntry));
  }

  public static <T> ImmutableSet<T> combine(
      final ImmutableSet<T> immutableSet1, final ImmutableSet<T> immutableSet2) {
    return new LinkedList<T>() {
      {
        addAll(immutableSet1);
        addAll(immutableSet2);
      }
    }.stream().collect(ImmutableSet.toImmutableSet());
  }

  public static ImmutableSet<LocalDate> generateAllDatesInRange(
      final LocalDate startDate, final LocalDate endDate) {
    return Stream.iterate(startDate, d -> d.plusDays(1))
        .limit(
            ChronoUnit.DAYS.between(
                startDate,
                // Increment by one because ChronoUnit.DAYS.between API
                // to date is exclusive
                endDate.plusDays(1)))
        .collect(ImmutableSet.toImmutableSet());
  }
}
