package com.rimanware.volcanoisland;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.util.Timeout;
import com.rimanware.volcanoisland.common.BookingConstraints;
import com.rimanware.volcanoisland.database.RollingMonthDatabaseActor;
import com.rimanware.volcanoisland.database.SingleDateDatabaseManagerActor;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseCommand;
import com.rimanware.volcanoisland.errors.APIErrorMessages;
import com.rimanware.volcanoisland.routes.AvailabilitiesRouteProvider;
import com.rimanware.volcanoisland.routes.BookingRouteProvider;
import com.rimanware.volcanoisland.routes.ConcatRouteProvider;
import com.rimanware.volcanoisland.routes.api.RouteProvider;
import com.rimanware.volcanoisland.services.AvailabilityServiceImpl;
import com.rimanware.volcanoisland.services.BookingServiceImpl;
import com.rimanware.volcanoisland.services.api.AvailabilityService;
import com.rimanware.volcanoisland.services.api.BookingService;
import com.rimanware.volcanoisland.services.requesthandlers.dispatchers.RequestHandlerDispatcherActorFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public final class VolcanoIslandApp {
  public static void main(final String[] args) throws IOException {
    final Config config = ConfigFactory.load("application.conf");
    final ActorSystem system = ActorSystem.create("routes", config);
    final Http http = Http.get(system);
    final ActorMaterializer materializer = ActorMaterializer.create(system);

    final String databaseFolderPath = "database";
    final Route route = initialize(LocalDate.now(), system, databaseFolderPath);

    final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = route.flow(system, materializer);
    final CompletionStage<ServerBinding> binding =
        http.bindAndHandle(routeFlow, ConnectHttp.toHost("localhost", 8080), materializer);

    System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
    System.in.read(); // let it run until user presses return

    binding
        .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
        .thenAccept(unbound -> system.terminate()); // and shutdown when done
  }

  public static Route initialize(
      final LocalDate currentDate, final ActorSystem system, final String databaseFolderPath) {
    final Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS));

    // This should be made configurable, but for the sake of simplicity it is hardcoded.
    final BookingConstraints bookingConstraints = BookingConstraints.INSTANCE;

    // This should be determined by a more sophisticated internationalization implementation, but
    // for the sake of simplicity it is hardcoded.
    final APIErrorMessages apiErrorMessages = APIErrorMessages.ENGLISH;

    // Wire everything together manually as we are not using an Dependency Injection framework
    final ActorRef rollingMonthDatabaseActor =
        system.actorOf(
            RollingMonthDatabaseActor.props(
                databaseFolderPath,
                BookingConstraints.INSTANCE,
                SingleDateDatabaseManagerActor::props),
            "RollingMonthDatabaseActor");
    rollingMonthDatabaseActor.tell(
        RollingMonthDatabaseCommand.start(currentDate), ActorRef.noSender());

    final AvailabilityService availabilityService =
        getAvailabilityService(system, timeout, rollingMonthDatabaseActor, apiErrorMessages);

    final BookingService bookingService =
        getBookingService(system, timeout, rollingMonthDatabaseActor, apiErrorMessages);

    return getRoute(availabilityService, bookingService, bookingConstraints, apiErrorMessages);
  }

  public static Route getRoute(
      final AvailabilityService availabilityService,
      final BookingService bookingService,
      final BookingConstraints bookingConstraints,
      final APIErrorMessages apiErrorMessages) {

    final RouteProvider availabilitiesRouteProvider =
        AvailabilitiesRouteProvider.create(
            availabilityService, bookingConstraints, apiErrorMessages);

    final RouteProvider bookingRouteProvider =
        BookingRouteProvider.create(bookingService, bookingConstraints, apiErrorMessages);

    return ConcatRouteProvider.create(availabilitiesRouteProvider, bookingRouteProvider)
        .getRoutes();
  }

  private static BookingService getBookingService(
          final ActorSystem system,
          final Timeout timeout,
          final ActorRef rollingMonthDatabaseActor,
          final APIErrorMessages apiErrorMessages) {
    final ActorRef createBookingRequestHandlerDispatcherActor =
        system.actorOf(
            RequestHandlerDispatcherActorFactory.createBookingRequestHandlerDispatcherActorProps(
                rollingMonthDatabaseActor, apiErrorMessages),
            "CreateBookingRequestHandlerDispatcherActor");

    final ActorRef updateBookingRequestHandlerDispatcherActor =
        system.actorOf(
            RequestHandlerDispatcherActorFactory.updateBookingRequestHandlerDispatcherActorProps(
                rollingMonthDatabaseActor, apiErrorMessages),
            "UpdateBookingRequestHandlerDispatcherActor");

    final ActorRef deleteBookingRequestHandlerDispatcherActor =
        system.actorOf(
            RequestHandlerDispatcherActorFactory.deleteBookingRequestHandlerDispatcherActorProps(
                rollingMonthDatabaseActor, apiErrorMessages),
            "DeleteBookingRequestHandlerDispatcherActor");

    return BookingServiceImpl.create(
        createBookingRequestHandlerDispatcherActor,
        updateBookingRequestHandlerDispatcherActor,
        deleteBookingRequestHandlerDispatcherActor,
        timeout);
  }

  private static AvailabilityService getAvailabilityService(
          final ActorSystem system,
          final Timeout timeout,
          final ActorRef rollingMonthDatabaseActor,
          final APIErrorMessages apiErrorMessages) {
    final ActorRef availabilityRequestHandlerDispatcherActor =
        system.actorOf(
            RequestHandlerDispatcherActorFactory.availabilityRequestHandlerDispatcherActorProps(
                rollingMonthDatabaseActor, apiErrorMessages),
            "AvailabilityRequestHandlerDispatcherActor");

    return AvailabilityServiceImpl.create(availabilityRequestHandlerDispatcherActor, timeout);
  }
}
