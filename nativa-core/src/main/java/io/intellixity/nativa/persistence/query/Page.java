package io.intellixity.nativa.persistence.query;

public sealed interface Page permits OffsetPage, SeekPage {
  int limit();
}

