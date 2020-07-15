package com.rimanware.volcanoisland.routes;

import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import com.rimanware.volcanoisland.routes.api.RouteProvider;

public final class ConcatRouteProvider extends AllDirectives implements RouteProvider {
  private final RouteProvider routeProviderA;
  private final RouteProvider routeProviderB;

  private ConcatRouteProvider(
      final RouteProvider routeProviderA, final RouteProvider routeProviderB) {
    this.routeProviderA = routeProviderA;
    this.routeProviderB = routeProviderB;
  }

  public static ConcatRouteProvider create(
      final RouteProvider routeProviderA, final RouteProvider routeProviderB) {
    return new ConcatRouteProvider(routeProviderA, routeProviderB);
  }

  @Override
  public Route getRoutes() {
    return route(routeProviderA.getRoutes(), routeProviderB.getRoutes());
  }
}
