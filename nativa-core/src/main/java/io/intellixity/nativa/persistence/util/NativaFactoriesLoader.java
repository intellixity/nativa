package io.intellixity.nativa.persistence.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * Minimal spring.factories-style loader for nativa.\n
 *
 * Looks up all {@code META-INF/nativa.factories} resources on the classpath.\n
 * Each resource is a Java Properties file of the form:\n
 *\n
 * <pre>\n
 * io.intellixity.nativa.persistence.authoring.UserTypeProvider=com.acme.MyProvider,com.acme.OtherProvider\n
 * io.intellixity.nativa.persistence.spi.bind.BinderProvider=com.acme.MyBinderProvider\n
 * </pre>\n
 *
 * Values may be comma-separated. Whitespace is ignored.\n
 */
public final class NativaFactoriesLoader {
  public static final String RESOURCE = "META-INF/nativa.factories";

  private NativaFactoriesLoader() {}

  public static <T> List<T> load(Class<T> spiType) {
    return load(spiType, Thread.currentThread().getContextClassLoader());
  }

  public static <T> List<T> load(Class<T> spiType, ClassLoader cl) {
    Objects.requireNonNull(spiType, "spiType");
    if (cl == null) cl = NativaFactoriesLoader.class.getClassLoader();

    String key = spiType.getName();
    List<String> implNames = new ArrayList<>();

    Enumeration<URL> resources;
    try {
      resources = cl.getResources(RESOURCE);
    } catch (IOException e) {
      throw new RuntimeException("Failed to enumerate " + RESOURCE, e);
    }

    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();
      Properties p = new Properties();
      try (InputStream in = url.openStream()) {
        p.load(in);
      } catch (IOException e) {
        throw new RuntimeException("Failed to load " + RESOURCE + " from " + url, e);
      }

      String v = p.getProperty(key);
      if (v == null || v.isBlank()) continue;
      for (String part : v.split(",")) {
        String name = part == null ? null : part.trim();
        if (name == null || name.isEmpty()) continue;
        implNames.add(name);
      }
    }

    // De-dupe while preserving order
    LinkedHashSet<String> uniq = new LinkedHashSet<>(implNames);
    List<T> out = new ArrayList<>(uniq.size());
    for (String implName : uniq) {
      out.add(newInstance(implName, spiType, cl));
    }
    return out;
  }

  private static <T> T newInstance(String implName, Class<T> spiType, ClassLoader cl) {
    try {
      Class<?> raw = Class.forName(implName, true, cl);
      if (!spiType.isAssignableFrom(raw)) {
        throw new IllegalArgumentException("Class " + implName + " does not implement " + spiType.getName());
      }
      @SuppressWarnings("unchecked")
      Class<? extends T> impl = (Class<? extends T>) raw;
      return impl.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Failed to instantiate " + implName + " for SPI " + spiType.getName(), e);
    }
  }
}


