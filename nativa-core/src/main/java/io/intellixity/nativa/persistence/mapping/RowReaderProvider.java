package io.intellixity.nativa.persistence.mapping;

import java.util.Map;

/**
 * Service-discovered provider for RowReaders.\n
 *
 * Key is the authoring {@code type} (EntityAuthoring.type).\n
 */
public interface RowReaderProvider {
  Map<String, RowReader<?>> rowReadersByType();
}




