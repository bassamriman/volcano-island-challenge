package com.rimanware.volcanoisland.nonfunctionaltests;

import akka.actor.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRouteResult;
import com.google.common.collect.ImmutableList;
import com.rimanware.volcanoisland.common.BookingConstraints;
import com.rimanware.volcanoisland.common.RequesterTestActor;
import com.rimanware.volcanoisland.common.RoutesTester;
import com.rimanware.volcanoisland.database.RollingMonthDatabaseActor;
import com.rimanware.volcanoisland.database.SingleDateDatabaseManagerActor;
import com.rimanware.volcanoisland.services.models.responses.Availabilities;
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

import static akka.pattern.PatternsCS.ask;

public class ConcurrencyTest extends RoutesTester {

  protected static final String dataBasePath = "test-database";

  @Test
  public void multiCreateShouldBeHandlerGracefully()
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

    final ImmutableList<Integer> statusCode =
        results.stream().map(TestRouteResult::statusCode).collect(ImmutableList.toImmutableList());

    Assert.assertTrue(
        "Should contain at least one successful request",
        statusCode.contains(StatusCodes.CREATED.intValue()));
    Assert.assertTrue(
        "Should contain at least one failed request",
        statusCode.contains(StatusCodes.BAD_REQUEST.intValue()));

    final Availabilities availabilities = getAvailabilities(date.minusDays(1), date.plusDays(1));
  }

  @Override
  public Config additionalConfig() {
    return ConfigFactory.load("application.conf");
  }

  @Override
  public void initialize() {
    // On Disk DataBase
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

  /*
  @AfterClass
  public static void afterClass() throws InterruptedException, IOException {
    // This is bad, but a shortcut for the sake of time
    // We have to wait for the actors to die before deleting the folder
    Thread.sleep(20000);
    FileUtils.forceDelete(new File(dataBasePath));
  }
   */
}
