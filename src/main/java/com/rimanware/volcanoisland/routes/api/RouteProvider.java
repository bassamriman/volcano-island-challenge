package com.rimanware.volcanoisland.routes.api;

import akka.http.javadsl.server.Route;

public interface RouteProvider {
  Route getRoutes();
}
