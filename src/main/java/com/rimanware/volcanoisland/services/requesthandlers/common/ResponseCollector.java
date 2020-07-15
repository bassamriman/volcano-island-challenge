package com.rimanware.volcanoisland.services.requesthandlers.common;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.rimanware.volcanoisland.common.UtilityFunctions;

public final class ResponseCollector<Response> {
  private final ImmutableSet<Response> collectedResponses;
  private final ImmutableSet<Response> expectedResponses;
  private final Boolean collectedAllExpectedResponses;

  private ResponseCollector(
      final ImmutableSet<Response> collectedResponses,
      final ImmutableSet<Response> expectedResponses) {
    this.collectedResponses = collectedResponses;
    this.expectedResponses = expectedResponses;
    this.collectedAllExpectedResponses = collectedResponses.containsAll(expectedResponses);
  }

  public static <Response> ResponseCollector<Response> create(
      final ImmutableSet<Response> collectedResponses,
      final ImmutableSet<Response> expectedResponses) {
    return new ResponseCollector<>(collectedResponses, expectedResponses);
  }

  public static <Response> ResponseCollector<Response> empty(
      final ImmutableSet<Response> expectedResponses) {
    return create(ImmutableSet.of(), expectedResponses);
  }

  public ResponseCollector<Response> collect(final ImmutableCollection<Response> responses) {

    if (expectedResponses.containsAll(responses)) {
      return ResponseCollector.create(
          UtilityFunctions.combine(
              collectedResponses, responses.stream().collect(ImmutableSet.toImmutableSet())),
          expectedResponses);
    } else {
      throw new IllegalStateException("Received a response that is not expected");
    }
  }

  public ResponseCollector<Response> collect(final Response response) {
    return collect(ImmutableList.of(response));
  }

  public Boolean collectedAllResponses() {
    return collectedAllExpectedResponses;
  }
}
