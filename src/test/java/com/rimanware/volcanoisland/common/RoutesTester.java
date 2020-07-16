package com.rimanware.volcanoisland.common;

import akka.actor.ActorRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;
import akka.util.Timeout;
import com.rimanware.volcanoisland.business.BookingConstraintsImpl;
import com.rimanware.volcanoisland.business.api.BookingConstraints;
import com.rimanware.volcanoisland.database.RollingMonthDatabaseActor;
import com.rimanware.volcanoisland.database.SingleDateDatabaseManagerActor;
import com.rimanware.volcanoisland.database.api.RollingMonthDatabaseCommand;
import com.rimanware.volcanoisland.errors.APIErrorMessagesImpl;
import com.rimanware.volcanoisland.errors.api.APIErrorMessages;
import com.rimanware.volcanoisland.routes.AvailabilitiesRouteProvider;
import com.rimanware.volcanoisland.routes.BookingRouteProvider;
import com.rimanware.volcanoisland.routes.ConcatRouteProvider;
import com.rimanware.volcanoisland.routes.api.RouteProvider;
import com.rimanware.volcanoisland.services.AvailabilityServiceImpl;
import com.rimanware.volcanoisland.services.BookingServiceImpl;
import com.rimanware.volcanoisland.services.models.responses.Availabilities;
import com.rimanware.volcanoisland.services.models.responses.BookingConfirmation;
import com.rimanware.volcanoisland.services.requesthandlers.dispatchers.RequestHandlerDispatcherActorFactory;
import org.junit.After;
import org.junit.Before;
import scala.concurrent.duration.FiniteDuration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public abstract class RoutesTester extends JUnitRouteTest {
  protected static final BookingConstraints bookingConstraints = BookingConstraintsImpl.INSTANCE;
  protected static final APIErrorMessages apiErrorMessages = APIErrorMessagesImpl.ENGLISH;
  protected static final Timeout timeout =
      Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS));

  protected static final LocalDate currentDate = LocalDate.of(2020, 2, 1);
  protected static final DateTimeFormatter dateFormatter =
      DateTimeFormatter.ofPattern("dd/MM/yyyy");

  protected TestRoute volcanoIslandApp;
  protected ActorRef rollingMonthDatabaseActor;
  protected ActorRef createBookingRequestHandlerDispatcherActor;
  protected ActorRef updateBookingRequestHandlerDispatcherActor;
  protected ActorRef deleteBookingRequestHandlerDispatcherActor;
  protected ActorRef availabilityRequestHandlerDispatcherActor;

  @Before
  public void initialize() {
    // In Memory
    rollingMonthDatabaseActor =
        system()
            .actorOf(
                RollingMonthDatabaseActor.propsInMemory(
                    BookingConstraintsImpl.INSTANCE, SingleDateDatabaseManagerActor::props),
                "RollingMonthDatabaseActor-" + UUID.randomUUID().toString());

    initializeRoutes(rollingMonthDatabaseActor);
  }

  public void initializeRoutes(final ActorRef rollingMonthDatabaseActor) {
    rollingMonthDatabaseActor.tell(
        RollingMonthDatabaseCommand.start(currentDate), ActorRef.noSender());

    createBookingRequestHandlerDispatcherActor =
        system()
            .actorOf(
                RequestHandlerDispatcherActorFactory
                    .createBookingRequestHandlerDispatcherActorProps(
                        rollingMonthDatabaseActor, apiErrorMessages),
                "CreateBookingRequestHandlerDispatcherActor-" + UUID.randomUUID().toString());

    updateBookingRequestHandlerDispatcherActor =
        system()
            .actorOf(
                RequestHandlerDispatcherActorFactory
                    .updateBookingRequestHandlerDispatcherActorProps(
                        rollingMonthDatabaseActor, apiErrorMessages),
                "UpdateBookingRequestHandlerDispatcherActor-" + UUID.randomUUID().toString());

    deleteBookingRequestHandlerDispatcherActor =
        system()
            .actorOf(
                RequestHandlerDispatcherActorFactory
                    .deleteBookingRequestHandlerDispatcherActorProps(
                        rollingMonthDatabaseActor, apiErrorMessages),
                "DeleteBookingRequestHandlerDispatcherActor-" + UUID.randomUUID().toString());

    availabilityRequestHandlerDispatcherActor =
        system()
            .actorOf(
                RequestHandlerDispatcherActorFactory.availabilityRequestHandlerDispatcherActorProps(
                    rollingMonthDatabaseActor, apiErrorMessages),
                "AvailabilityRequestHandlerDispatcherActor-" + UUID.randomUUID().toString());

    final RouteProvider availabilitiesRouteProvider =
        AvailabilitiesRouteProvider.create(
            AvailabilityServiceImpl.create(availabilityRequestHandlerDispatcherActor, timeout),
            apiErrorMessages);

    final RouteProvider bookingRouteProvider =
        BookingRouteProvider.create(
            BookingServiceImpl.create(
                createBookingRequestHandlerDispatcherActor,
                updateBookingRequestHandlerDispatcherActor,
                deleteBookingRequestHandlerDispatcherActor,
                timeout),
            bookingConstraints,
            apiErrorMessages);

    volcanoIslandApp =
        testRoute(
            ConcatRouteProvider.create(availabilitiesRouteProvider, bookingRouteProvider)
                .getRoutes());
  }

  @After
  public void afterTest() {
    cleanUpActors();
  }

  public void cleanUpActors() {
    system().stop(rollingMonthDatabaseActor);
    system().stop(createBookingRequestHandlerDispatcherActor);
    system().stop(updateBookingRequestHandlerDispatcherActor);
    system().stop(deleteBookingRequestHandlerDispatcherActor);
    system().stop(availabilityRequestHandlerDispatcherActor);
  }

  protected final BookingConfirmation getBookingConfirmation(
      final LocalDate arrivalDate, final LocalDate departureDate) {
    return create(arrivalDate, departureDate)
        .assertStatusCode(StatusCodes.CREATED)
        .entity(Jackson.unmarshaller(BookingConfirmation.class));
  }

  protected final TestRouteResult create(
      final LocalDate arrivalDate, final LocalDate departureDate) {
    return volcanoIslandApp.run(createRequest(arrivalDate, departureDate));
  }

  protected final HttpRequest createRequest(
      final LocalDate arrivalDate, final LocalDate departureDate) {
    return HttpRequest.POST("/bookings")
        .withEntity(
            MediaTypes.APPLICATION_JSON.toContentType(),
            "{\n"
                + "    \"fullName\":\"Bassam Riman\",\n"
                + "    \"email\":\"bassam.riman@gmail.com\",\n"
                + "    \"arrivalDate\":\""
                + arrivalDate.format(dateFormatter)
                + "\",\n"
                + "    \"departureDate\":\""
                + departureDate.format(dateFormatter)
                + "\"\n"
                + "}");
  }

  protected final Availabilities getAvailabilities(
      final LocalDate arrivalDate, final LocalDate departureDate) {
    return volcanoIslandApp
        .run(getAvailabilitiesRequest(arrivalDate, departureDate))
        .assertStatusCode(StatusCodes.OK)
        .entity(Jackson.unmarshaller(Availabilities.class));
  }

  protected final HttpRequest getAvailabilitiesRequest(final LocalDate arrivalDate, final LocalDate departureDate) {
    return HttpRequest.GET("/availabilities")
        .withEntity(
            MediaTypes.APPLICATION_JSON.toContentType(),
            "{\n"
                + "    \"startDate\":\""
                + arrivalDate.format(dateFormatter)
                + "\",\n"
                + "    \"endDate\":\""
                + departureDate.format(dateFormatter)
                + "\"\n"
                + "}");
  }
}
