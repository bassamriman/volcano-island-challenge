package com.rimanware.volcanoisland.services.models.responses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class DateErrors {
  private final List<DateError> dateErrors;

  @JsonCreator
  private DateErrors(@JsonProperty("dateErrors") final List<DateError> dateErrors) {
    this.dateErrors = dateErrors;
  }

  public static DateErrors create(final List<DateError> dateErrors) {
    return new DateErrors(dateErrors);
  }

  public List<DateError> getDateErrors() {
    return dateErrors;
  }

  @Override
  public String toString() {
    return "DateErrors{" + "dateErrors=" + dateErrors + '}';
  }
}
