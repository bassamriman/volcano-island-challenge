package com.rimanware.volcanoisland.errors.api;

import akka.http.javadsl.model.StatusCode;

public interface APIError {
  String getKey();

  StatusCode getHttpStatusCode();
}
