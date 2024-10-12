package org.jets.processor.domain;

import java.util.List;

public sealed interface DefInfo permits ClassInfo{
  String name();

  List<? extends ComponentInfo> components();

  /**
   * @return true if all required types are resolved.
   */
  boolean resolved();
}
