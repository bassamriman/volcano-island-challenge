package com.rimanware.volcanoisland.services.models.responses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.time.LocalDate;

public final class Availability {
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  @JsonDeserialize(using = LocalDateDeserializer.class)
  @JsonSerialize(using = LocalDateSerializer.class)
  private final LocalDate availableDate;

  @JsonCreator
  private Availability(@JsonProperty("availableDate") final LocalDate availableDate) {
    this.availableDate = availableDate;
  }

  public static Availability create(final LocalDate availableDate) {
    return new Availability(availableDate);
  }

  public LocalDate getAvailableDate() {
    return availableDate;
  }
}
