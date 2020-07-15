package com.rimanware.volcanoisland.services.models.responses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rimanware.volcanoisland.errors.APIError;
import com.rimanware.volcanoisland.errors.APIErrorMessages;

import java.util.Objects;

public final class SimpleError {
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private final String error;

  @JsonCreator
  private SimpleError(@JsonProperty("error") final String error) {
    this.error = error;
  }

  public static SimpleError create(final String error) {
    return new SimpleError(error);
  }

  public static SimpleError create(final APIError error, final APIErrorMessages apiErrorMessages) {
    return new SimpleError(apiErrorMessages.getErrorMessage(error));
  }

  @Override
  public String toString() {
    return "SimpleError{" + "error='" + error + '\'' + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SimpleError that = (SimpleError) o;
    return error.equals(that.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(error);
  }
}
