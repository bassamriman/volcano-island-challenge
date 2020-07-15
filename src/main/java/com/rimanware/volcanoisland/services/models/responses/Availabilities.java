package com.rimanware.volcanoisland.services.models.responses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class Availabilities {
  private final List<Availability> availabilities;

  @JsonCreator
  private Availabilities(@JsonProperty("availabilities") final List<Availability> availabilities) {
    this.availabilities = availabilities;
  }

  public static Availabilities create(final List<Availability> availabilities) {
    return new Availabilities(availabilities);
  }

  public List<Availability> getAvailabilities() {
    return availabilities;
  }

  @Override
  public String toString() {
    return "Availabilities{" + "availabilities=" + availabilities + '}';
  }
}
