package com.rimanware.volcanoisland.services.requesthandlers.common;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import com.google.common.collect.ImmutableList;
import com.rimanware.volcanoisland.common.LoggingReceiveActor;
import com.rimanware.volcanoisland.database.api.SingleDateDatabaseCommand;
import com.rimanware.volcanoisland.services.requesthandlers.api.RequestHandlerResponse;

import java.time.LocalDate;
import java.util.function.Function;

public abstract class RequestHandlerActor<RequestState extends SenderProvider>
    extends LoggingReceiveActor {

  protected abstract Receive collectingResponses(
      final ResponseCollector<String> currentResponseCollector,
      final RequestState currentAvailabilityRequestState);

  protected final void nextStateOrCompleteRequest(
      final ResponseCollector<String> newResponseCollector, final RequestState requestState) {
    if (newResponseCollector.collectedAllResponses()) {
      handleResult(requestState);
    } else {
      getContext().become(collectingResponses(newResponseCollector, requestState));
    }
  }

  protected final void nextStateOrCompleteRequestWithRollback(
      final ResponseCollector<String> newResponseCollector,
      final RequestState requestState,
      final Function<RequestState, ImmutableList<LocalDate>> rollBackDatesExtractor,
      final ActorRef database) {
    if (newResponseCollector.collectedAllResponses()) {
      handleResultWithRollBackOnFailure(
          requestState, rollBackDatesExtractor.apply(requestState), database);
    } else {
      getContext().become(collectingResponses(newResponseCollector, requestState));
    }
  }

  protected final void handleResult(final RequestState requestState) {
    final RequestHandlerResponse response = createResponse(requestState);
    respondToSenderAndTerminate(requestState, response);
  }

  private void handleResultWithRollBackOnFailure(
      final RequestState requestState,
      final ImmutableList<LocalDate> dateToRollback,
      final ActorRef database) {
    final RequestHandlerResponse response = createResponse(requestState);

    // Save or Rollback newly booked dates depending if it's failure or not
    persistOrRollbackResult(response, dateToRollback, database);

    // Inform sender of failure
    respondToSenderAndTerminate(requestState, response);
  }

  private void persistOrRollbackResult(
      final RequestHandlerResponse response,
      final ImmutableList<LocalDate> dateToRollback,
      final ActorRef database) {
    if (response instanceof RequestHandlerResponse.Failure) {
      // Rollback updated dates
      dateToRollback.forEach(date -> database.tell(SingleDateDatabaseCommand.revert(date), self()));
    } else {
      // Commit all changes
      dateToRollback.forEach(date -> database.tell(SingleDateDatabaseCommand.commit(date), self()));
    }
  }

  private void respondToSenderAndTerminate(
      final RequestState requestState, final RequestHandlerResponse response) {
    // Send response to sender
    requestState.getSender().tell(response, self());

    // We are done handling the request this actor will suicide
    self().tell(PoisonPill.getInstance(), self());
  }

  protected abstract RequestHandlerResponse createResponse(RequestState requestState);
}
