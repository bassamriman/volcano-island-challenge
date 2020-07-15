package com.rimanware.volcanoisland.database.models;

import com.rimanware.volcanoisland.services.models.requests.BookingRequest;
import com.rimanware.volcanoisland.services.models.requests.UpdateBookingRequest;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/** Booking is a data model that a booking id to a user */
public class Booking implements Serializable {
  private final String id;
  private final String email;
  private final String fullName;
  private final LocalDate arrivalDate;
  private final LocalDate departureDate;

  public Booking(
      final String id,
      final String email,
      final String fullName,
      final LocalDate arrivalDate,
      final LocalDate departureDate) {
    this.id = id;
    this.email = email;
    this.fullName = fullName;
    this.arrivalDate = arrivalDate;
    this.departureDate = departureDate;
  }

  /**
   * Booking factory method
   *
   * @param email Email of the user that is booking
   * @param fullName Name of the user that is booking
   * @return Booking
   */
  public static Booking create(
      final String id,
      final String email,
      final String fullName,
      final LocalDate arrivalDate,
      final LocalDate departureDate) {
    return new Booking(id, email, fullName, arrivalDate, departureDate);
  }

  /**
   * Booking factory method with generated a random id
   *
   * @param email Email of the user that is booking
   * @param fullName Name of the user that is booking
   * @return Booking
   */
  public static Booking create(
      final String email,
      final String fullName,
      final LocalDate arrivalDate,
      final LocalDate departureDate) {
    return create(UUID.randomUUID().toString(), email, fullName, arrivalDate, departureDate);
  }

  public static Booking fromBookingRequest(final BookingRequest bookingRequest) {
    return create(
        bookingRequest.getEmail(),
        bookingRequest.getFullName(),
        bookingRequest.getArrivalDate(),
        bookingRequest.getDepartureDate());
  }

  public static Booking fromUpdateRequest(final UpdateBookingRequest updateBookingRequest) {
    return create(
        updateBookingRequest.getId(),
        updateBookingRequest.getBookingRequest().getEmail(),
        updateBookingRequest.getBookingRequest().getFullName(),
        updateBookingRequest.getBookingRequest().getArrivalDate(),
        updateBookingRequest.getBookingRequest().getDepartureDate());
  }

  /** @return true if date is withing booking period, else false. */
  public Boolean within(final LocalDate date) {
    return !(date.isBefore(arrivalDate) || date.isAfter(departureDate));
  }

  public String getId() {
    return id;
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

  @Override
  public String toString() {
    return "Booking{"
        + "id='"
        + id
        + '\''
        + ", email='"
        + email
        + '\''
        + ", fullName='"
        + fullName
        + '\''
        + '}';
  }
}
