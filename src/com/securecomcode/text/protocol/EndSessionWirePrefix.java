package com.securecomcode.text.protocol;

public class EndSessionWirePrefix extends WirePrefix {
  @Override
  public String calculatePrefix(String message) {
    return super.calculateEndSessionPrefix(message);
  }
}
