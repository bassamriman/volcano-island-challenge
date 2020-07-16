package com.rimanware.volcanoisland.services.models.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.time.LocalDate;

public interface AvailabilitiesRequest {

  static AvailabilitiesRequest.Empty empty() {
    return Empty.INSTANCE;
  }

  static AvailabilitiesRequest.DateRange dateRange(final LocalDate start, final LocalDate end) {
    return AvailabilitiesRequest.dateRange(start, end);
  }

  enum Empty implements AvailabilitiesRequest {
    INSTANCE;

    Empty() {}

    @Override
    public String toString() {
      return "AvailabilitiesRequest.Empty{}";
    }
  }

  final class DateRange implements AvailabilitiesRequest {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonSerialize(using = LocalDateSerializer.class)
    private final LocalDate startDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonSerialize(using = LocalDateSerializer.class)
    private final LocalDate endDate;

    @JsonCreator
    private DateRange(
        @JsonProperty("startDate") final LocalDate startDate,
        @JsonProperty("endDate") final LocalDate endDate) {
      this.startDate = startDate;
      this.endDate = endDate;
    }

    public static DateRange create(final LocalDate start, final LocalDate end) {
      return new DateRange(start, end);
    }

    public LocalDate getStartDate() {
      return startDate;
    }

    public LocalDate getEndDate() {
      return endDate;
    }

    @Override
    public String toString() {
      return "AvailabilitiesRequest.DateRange{"
          + "startDate="
          + startDate
          + ", endDate="
          + endDate
          + '}';
    }
  }
}
