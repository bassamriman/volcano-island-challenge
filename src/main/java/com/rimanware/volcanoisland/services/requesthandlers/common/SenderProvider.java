package com.rimanware.volcanoisland.services.requesthandlers.common;

import akka.actor.ActorRef;

public interface SenderProvider {
  ActorRef getSender();
}
