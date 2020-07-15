package com.rimanware.volcanoisland.common;

public final class Tuple<LEFT, RIGHT> {
  private final LEFT left;
  private final RIGHT right;

  private Tuple(final LEFT left, final RIGHT right) {
    this.left = left;
    this.right = right;
  }

  public static <LEFT, RIGHT> Tuple<LEFT, RIGHT> create(final LEFT left, final RIGHT right) {
    return new Tuple<>(left, right);
  }

  public LEFT getLeft() {
    return left;
  }

  public RIGHT getRight() {
    return right;
  }

  @Override
  public String toString() {
    return "Tuple{" + "left=" + left + ", right=" + right + '}';
  }
}
