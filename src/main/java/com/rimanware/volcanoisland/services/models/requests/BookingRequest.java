package com.rimanware.volcanoisland.services.models.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.time.LocalDate;

public final class BookingRequest {
  private final String email;
  private final String fullName;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  @JsonDeserialize(using = LocalDateDeserializer.class)
  @JsonSerialize(using = LocalDateSerializer.class)
  private final LocalDate arrivalDate;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  @JsonDeserialize(using = LocalDateDeserializer.class)
  @JsonSerialize(using = LocalDateSerializer.class)
  private final LocalDate departureDate;

  @JsonCreator
  private BookingRequest(
      @JsonProperty("email") final String email,
      @JsonProperty("fullName") final String fullName,
      @JsonProperty("arrivalDate") final LocalDate arrivalDate,
      @JsonProperty("departureDate") final LocalDate departureDate) {

    this.email = email;
    this.fullName = fullName;
    this.arrivalDate = arrivalDate;
    this.departureDate = departureDate;
  }

  public static BookingRequest create(
      final String email,
      final String fullName,
      final LocalDate arrivalDate,
      final LocalDate departureDate) {
    return new BookingRequest(email, fullName, arrivalDate, departureDate);
  }

  public String getEmail() {
    return email;
  }

  public String getFullName() {
    return fullName;
  }

  public LocalDate getArrivalDate() {
    return arrivalDate;
  }

  public LocalDate getDepartureDate() {
    return departureDate;
  }
}
