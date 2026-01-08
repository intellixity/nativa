package io.intellixity.nativa.persistence.codegen.internal;

import java.io.*;
import java.nio.file.*;

public final class JavaFiles {
  private JavaFiles() {}

  public static Path filePath(Path outDir, String pkg, String simpleName) {
    Path dir = outDir;
    if (pkg != null && !pkg.isBlank()) dir = outDir.resolve(pkg.replace('.', '/'));
    return dir.resolve(simpleName + ".java");
  }

  public static IndentedWriter open(Path outDir, String pkg, String simpleName) throws IOException {
    Path file = filePath(outDir, pkg, simpleName);
    Files.createDirectories(file.getParent());
    return new IndentedWriter(Files.newBufferedWriter(file));
  }

  public static final class IndentedWriter implements Closeable {
    private final Writer w;
    private int indent;

    public IndentedWriter(Writer w) { this.w = w; }

    public void println(String s) throws IOException {
      for (int i = 0; i < indent; i++) w.write("  ");
      w.write(s);
      w.write("\n");
    }

    public void blank() throws IOException { w.write("\n"); }

    public void indent() { indent++; }
    public void outdent() { indent = Math.max(0, indent - 1); }

    @Override public void close() throws IOException { w.close(); }
  }
}

