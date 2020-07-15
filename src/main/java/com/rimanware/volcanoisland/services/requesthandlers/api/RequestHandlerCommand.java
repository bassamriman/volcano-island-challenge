package com.rimanware.volcanoisland.services.requesthandlers.api;

public interface RequestHandlerCommand {
  static Process process() {
    return Process.INSTANCE;
  }

  enum Process implements RequestHandlerCommand {
    INSTANCE;

    Process() {}

    @Override
    public String toString() {
      return "Process{}";
    }
  }
}
