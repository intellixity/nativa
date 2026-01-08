package io.intellixity.nativa.persistence.jdbc;

import io.intellixity.nativa.persistence.compile.Bind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compiles a SQL string containing named parameters (e.g. :tenantId) into JDBC SQL with '?' binds.
 *
 * Rules (MVP):
 * - Params are recognized as ':' followed by [A-Za-z_][A-Za-z0-9_]*\n
 * - '::' is treated as a SQL cast and not a param.\n
 * - Params inside single quotes are ignored.\n
 *
 * UserType handling:
 * - If param value is a {@link Bind}, it is used directly.\n
 * - Otherwise, framework infers userTypeId from runtime value.\n
 */
public final class ViewSqlParamCompiler {
  private ViewSqlParamCompiler() {}

  /**
   * Resolve binds for named params in a SQL string without rewriting the SQL.
   * Bind order matches appearance order of named params.
   */
  public static List<Bind> bindsFor(String sql, Map<String, Object> params) {
    if (sql == null) return List.of();
    Map<String, Object> effective = (params == null) ? Map.of() : params;

    List<Bind> binds = new ArrayList<>();
    boolean inSingleQuote = false;

    for (int i = 0; i < sql.length(); i++) {
      char ch = sql.charAt(i);

      if (ch == '\'') {
        // Handle '' escape
        if (inSingleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
          i++;
          continue;
        }
        inSingleQuote = !inSingleQuote;
        continue;
      }

      if (!inSingleQuote && ch == ':') {
        // Skip :: casts
        if (i + 1 < sql.length() && sql.charAt(i + 1) == ':') {
          i++;
          continue;
        }

        int start = i + 1;
        if (start < sql.length() && isIdentStart(sql.charAt(start))) {
          int end = start + 1;
          while (end < sql.length() && isIdentPart(sql.charAt(end))) end++;
          String name = sql.substring(start, end);

          Object raw = getRequired(effective, name);
          Bind b = (raw instanceof Bind bb) ? bb : inferBind(raw);
          binds.add(b);
          i = end - 1;
        }
      }
    }

    return binds;
  }

  /**
   * Rewrite a SQL string containing named params (":name") into JDBC SQL with '?' placeholders.\n
   * Purely lexical scanning.\n
   */
  public static String toJdbcSql(String sql) {
    if (sql == null) return "";
    StringBuilder out = new StringBuilder(sql.length() + 16);
    boolean inSingleQuote = false;

    for (int i = 0; i < sql.length(); i++) {
      char ch = sql.charAt(i);

      if (ch == '\'') {
        // Handle '' escape
        if (inSingleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
          out.append("''");
          i++;
          continue;
        }
        inSingleQuote = !inSingleQuote;
        out.append(ch);
        continue;
      }

      if (!inSingleQuote && ch == ':') {
        // Skip :: casts
        if (i + 1 < sql.length() && sql.charAt(i + 1) == ':') {
          out.append("::");
          i++;
          continue;
        }

        int start = i + 1;
        if (start < sql.length() && isIdentStart(sql.charAt(start))) {
          int end = start + 1;
          while (end < sql.length() && isIdentPart(sql.charAt(end))) end++;
          out.append('?');
          i = end - 1;
          continue;
        }
      }

      out.append(ch);
    }

    return out.toString();
  }

  /** Back-compat helper: compile view SQL into JDBC SQL + ordered binds. */
  public static SqlStatement compile(String sql, Map<String, Object> params) {
    return new SqlStatement(toJdbcSql(sql), bindsFor(sql, params));
  }

  private static Object getRequired(Map<String, Object> params, String name) {
    if (params.containsKey(name)) return params.get(name);
    throw new IllegalArgumentException("Missing query param: " + name);
  }

  private static boolean isIdentStart(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
  }

  private static boolean isIdentPart(char c) {
    return isIdentStart(c) || (c >= '0' && c <= '9');
  }

  private static Bind inferBind(Object v) {
    if (v == null) return new Bind(null, "string");
    if (v instanceof String) return new Bind(v, "string");
    if (v instanceof Integer) return new Bind(v, "int");
    if (v instanceof Long) return new Bind(v, "long");
    if (v instanceof Boolean) return new Bind(v, "bool");
    if (v instanceof java.util.UUID) return new Bind(v, "uuid");
    if (v instanceof java.time.Instant) return new Bind(v, "instant");

    // Default to json for map/list/pojo-like objects
    if (v instanceof List<?> || v instanceof Map<?, ?>) {
      return new Bind(v, "json");
    }
    return new Bind(v, "json");
  }
}


