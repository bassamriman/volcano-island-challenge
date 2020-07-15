package com.rimanware.volcanoisland.nonfunctionaltests;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRouteResult;
import com.google.common.collect.ImmutableList;
import com.rimanware.volcanoisland.common.BookingConstraints;
import com.rimanware.volcanoisland.common.RequesterTestActor;
import com.rimanware.volcanoisland.common.RoutesTester;
import com.rimanware.volcanoisland.common.Tuple;
import com.rimanware.volcanoisland.database.RollingMonthDatabaseActor;
import com.rimanware.volcanoisland.database.SingleDateDatabaseManagerActor;
import com.rimanware.volcanoisland.services.models.responses.Availabilities;
import com.rimanware.volcanoisland.services.models.responses.BookingConfirmation;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static akka.pattern.PatternsCS.ask;

public class ConcurrencyTest extends RoutesTester {

  protected static final String dataBasePath = "test-database";

  @Test
  public void multiCreateBookingShouldSucceedWithOnlyOneSuccessfulWriteGivenTheyOverlap()
      throws ExecutionException, InterruptedException {

    final LocalDate date =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(2);

    final HttpRequest firstRequest = createRequest(date.minusDays(1), date);
    final ActorRef firstRequester =
        system().actorOf(RequesterTestActor.props(volcanoIslandApp, firstRequest));

    final HttpRequest secondRequest = createRequest(date, date.plusDays(1));
    final ActorRef secondRequester =
        system().actorOf(RequesterTestActor.props(volcanoIslandApp, secondRequest));

    // Async start both at the same time
    final CompletableFuture<TestRouteResult> firstRequesterResultFuture =
        ask(firstRequester, RequesterTestActor.start(), timeout)
            .thenApply((TestRouteResult.class::cast))
            .toCompletableFuture();

    final CompletableFuture<TestRouteResult> secondRequesterResultFuture =
        ask(secondRequester, RequesterTestActor.start(), timeout)
            .thenApply((TestRouteResult.class::cast))
            .toCompletableFuture();

    final CompletionStage<List<TestRouteResult>> combinedDataCompletionStage =
        CompletableFuture.allOf(firstRequesterResultFuture, secondRequesterResultFuture)
            .thenApply(
                ignoredVoid ->
                    Arrays.asList(
                        firstRequesterResultFuture.join(), secondRequesterResultFuture.join()));

    final List<TestRouteResult> results = combinedDataCompletionStage.toCompletableFuture().get();

    // Assert
    final ImmutableList<Integer> statusCode =
        results.stream().map(TestRouteResult::statusCode).collect(ImmutableList.toImmutableList());

    Assert.assertTrue(
        "Should contain at least one successful request",
        statusCode.contains(StatusCodes.CREATED.intValue()));
    Assert.assertTrue(
        "Should contain at least one failed request",
        statusCode.contains(StatusCodes.BAD_REQUEST.intValue()));
  }

  @Test
  public void bookingAllThirtyDaysConcurrentlyShouldSucceedConcurrentlyGivenTheyDontOverlap()
      throws ExecutionException, InterruptedException {

    // Initialize all requesters
    final ImmutableList<ActorRef> requesters =
        bookingConstraints.generateAllReservableDays(currentDate).stream()
            .map(
                bookingDate -> {
                  final HttpRequest request = createRequest(bookingDate, bookingDate);
                  final ActorRef requester =
                      system().actorOf(RequesterTestActor.props(volcanoIslandApp, request));
                  return requester;
                })
            .collect(ImmutableList.toImmutableList());

    // Execute all requests at the same time
    final ImmutableList<CompletableFuture<TestRouteResult>> resultFutures =
        requesters.stream()
            .map(
                requester ->
                    ask(requester, RequesterTestActor.start(), timeout)
                        .thenApply((TestRouteResult.class::cast))
                        .toCompletableFuture())
            .collect(ImmutableList.toImmutableList());

    final CompletionStage<List<TestRouteResult>> combinedDataCompletionStage =
        waitOnAll(resultFutures);

    final List<TestRouteResult> results = combinedDataCompletionStage.toCompletableFuture().get();

    final ImmutableList<Integer> statusCode =
        results.stream().map(TestRouteResult::statusCode).collect(ImmutableList.toImmutableList());

    // Assert
    Assert.assertTrue(
        "Should contain at least one successful request",
        statusCode.contains(StatusCodes.CREATED.intValue()));
    Assert.assertFalse(
        "Should contain at least one failed request",
        statusCode.contains(StatusCodes.BAD_REQUEST.intValue()));

    final Availabilities allAvailabilities =
        getAvailabilities(
            bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate),
            bookingConstraints.endDateOfReservationWindowGivenCurrentDate(currentDate));

    Assert.assertTrue("All date should be booked", allAvailabilities.getAvailabilities().isEmpty());

    // Kill all requesters
    requesters.forEach(requester -> system().stop(requester));
  }

  @Test
  /**
   * This a Benchmark test. DON'T TRY THIS AT HOME. This shouldn't be a unit test as there is a lot
   * of randomness. This should be a regression test that runs 100 time and taking the average. This
   * proves that the app can write 30 request concurrently.
   */
  public void bookingAllThirtyDaysConcurrentlyShouldNotScaleWithTheAmountOfBooking() {

    final long maxScaleFactor = 3;
    final ActorSystem requesterActorSystem = ActorSystem.apply("Test");

    // Create first booking for benchmarking
    final LocalDate createArrivalDate =
        bookingConstraints.startDateOfReservationWindowGivenCurrentDate(currentDate).plusDays(1);
    final LocalDate createDepartureDate = createArrivalDate.plusDays(2);

    // ----------------------------
    // WARM-UP BOOKING
    // ----------------------------
    final BookingConfirmation warmUpBookingConfirmation =
        createBookingWithConfirmation(createArrivalDate, createDepartureDate);
    // Delete booking
    deleteBooking(warmUpBookingConfirmation);

    // ----------------------------
    // SINGLE BOOKING BENCHMARK
    // ----------------------------
    final Tuple<BookingConfirmation, Long> bookingConfirmationWithTime =
        timedInMilliseconds(
            () -> createBookingWithConfirmation(createArrivalDate, createDepartureDate));

    final BookingConfirmation bookingConfirmation = bookingConfirmationWithTime.getLeft();
    final Long singleBookingDurationInMilliseconds = bookingConfirmationWithTime.getRight();
    // Delete booking
    deleteBooking(bookingConfirmation);

    // ----------------------------
    // MULTIPLE BOOKING BENCHMARK
    // ----------------------------
    // Initialize all requesters
    final ImmutableList<ActorRef> requesters =
        bookingConstraints.generateAllReservableDays(currentDate).stream()
            .map(
                bookingDate -> {
                  final HttpRequest request = createRequest(bookingDate, bookingDate);
                  return requesterActorSystem.actorOf(
                      RequesterTestActor.props(volcanoIslandApp, request));
                })
            .collect(ImmutableList.toImmutableList());

    // Execute all requests at the same time
    final Tuple<List<TestRouteResult>, Long> testRouteResultWithTime =
        timedInMilliseconds(() -> startExecuting(requesters));

    final Long thirtyBookingDurationInMilliseconds = testRouteResultWithTime.getRight();

    Assert.assertTrue(
        "The execution time should not scale with amount of booking",
        thirtyBookingDurationInMilliseconds
            < (singleBookingDurationInMilliseconds
                * bookingConstraints.generateAllReservableDays(currentDate).size()
                / maxScaleFactor));

    // Kill all requester actor system
    requesterActorSystem.terminate();
  }

  private void deleteBooking(final BookingConfirmation warmUpBookingConfirmation) {
    volcanoIslandApp
        .run(
            HttpRequest.DELETE("/bookings/" + warmUpBookingConfirmation.getBookingConfirmationId()))
        .assertStatusCode(StatusCodes.OK);
  }

  private BookingConfirmation createBookingWithConfirmation(
          final LocalDate createArrivalDate, final LocalDate createDepartureDate) {
    return volcanoIslandApp
        .run(createRequest(createArrivalDate, createDepartureDate))
        .assertStatusCode(StatusCodes.CREATED)
        .entity(Jackson.unmarshaller(BookingConfirmation.class));
  }

  private List<TestRouteResult> startExecuting(final ImmutableList<ActorRef> requesters) {
    final ImmutableList<CompletableFuture<TestRouteResult>> resultFutures =
        requesters.stream()
            .map(
                requester ->
                    ask(requester, RequesterTestActor.start(), timeout)
                        .thenApply((TestRouteResult.class::cast))
                        .toCompletableFuture())
            .collect(ImmutableList.toImmutableList());

    final CompletionStage<List<TestRouteResult>> combinedDataCompletionStage =
        waitOnAll(resultFutures);

    try {
      return combinedDataCompletionStage.toCompletableFuture().get();
    } catch (final InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    return ImmutableList.of();
  }

  private <T> Tuple<T, Long> timedInMilliseconds(final Supplier<T> function) {
    final long startTime = System.nanoTime();
    final T result = function.get();
    final long endTime = System.nanoTime();
    final long timeElapsed = endTime - startTime;
    return Tuple.create(result, timeElapsed / 1000000);
  }

  private <T> CompletableFuture<List<T>> waitOnAll(final List<CompletableFuture<T>> futuresList) {
    final CompletableFuture<Void> allFuturesResult =
        CompletableFuture.allOf(futuresList.toArray(new CompletableFuture[futuresList.size()]));
    return allFuturesResult.thenApply(
        v -> futuresList.stream().map(future -> future.join()).collect(Collectors.<T>toList()));
  }

  @Override
  public Config additionalConfig() {
    return ConfigFactory.load("application.conf");
  }

  @Override
  public void initialize() {
    // On Disk Database
    rollingMonthDatabaseActor =
        system()
            .actorOf(
                RollingMonthDatabaseActor.props(
                    dataBasePath + "/test-" + UUID.randomUUID().toString(),
                    BookingConstraints.INSTANCE,
                    SingleDateDatabaseManagerActor::props),
                "RollingMonthDatabaseActor-" + UUID.randomUUID().toString());

    initializeRoutes(rollingMonthDatabaseActor);
  }
}
