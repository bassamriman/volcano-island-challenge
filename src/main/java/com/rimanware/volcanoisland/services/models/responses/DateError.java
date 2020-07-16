package com.rimanware.volcanoisland.services.models.responses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.rimanware.volcanoisland.errors.api.APIError;
import com.rimanware.volcanoisland.errors.api.APIErrorMessages;

import java.time.LocalDate;
import java.util.Objects;

public final class DateError {
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  @JsonDeserialize(using = LocalDateDeserializer.class)
  @JsonSerialize(using = LocalDateSerializer.class)
  private final LocalDate date;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private final String error;

  @JsonCreator
  private DateError(
      @JsonProperty("date") final LocalDate date, @JsonProperty("error") final String error) {
    this.date = date;
    this.error = error;
  }

  public static DateError create(final LocalDate date, final String error) {
    return new DateError(date, error);
  }

  public static DateError create(
      final LocalDate date, final APIError error, final APIErrorMessages apiErrorMessages) {
    return new DateError(date, apiErrorMessages.getErrorMessage(error));
  }

  public LocalDate getDate() {
    return date;
  }

  public String getError() {
    return error;
  }

  @Override
  public String toString() {
    return "DateError{" + "date=" + date + ", error='" + error + '\'' + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final DateError dateError = (DateError) o;
    return date.equals(dateError.date) && error.equals(dateError.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(date, error);
  }
}
