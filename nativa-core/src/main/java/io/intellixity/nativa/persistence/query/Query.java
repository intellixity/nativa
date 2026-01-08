package io.intellixity.nativa.persistence.query;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.intellixity.nativa.persistence.query.aggregation.GroupBy;

import java.util.*;

@JsonSerialize(using = QueryJsonSerializer.class)
@JsonDeserialize(using = QueryJsonDeserializer.class)
public final class Query implements QueryElement {
  private QueryElement filter;
  private Page page;
  private List<String> projection = new ArrayList<>();
  private List<SortField> sort = new ArrayList<>();
  private GroupBy groupBy;
  private Map<String, Object> params = new LinkedHashMap<>();

  public Query() {}

  public QueryElement filter() { return filter; }
  public Page page() { return page; }
  public List<String> projection() { return projection; }
  public List<SortField> sort() { return sort; }
  public GroupBy groupBy() { return groupBy; }
  /** Named params for view SQL (e.g. :tenantId) and QueryValues.Param resolution. */
  public Map<String, Object> params() { return params; }

  public Query withFilter(QueryElement filter) { this.filter = filter; return this; }
  public Query withPage(Page page) { this.page = page; return this; }
  public Query withProjection(List<String> projection) { this.projection = new ArrayList<>(projection == null ? List.of() : projection); return this; }
  public Query withSort(List<SortField> sort) { this.sort = new ArrayList<>(sort == null ? List.of() : sort); return this; }
  public Query withGroupBy(GroupBy groupBy) { this.groupBy = groupBy; return this; }
  public Query withParams(Map<String, Object> params) { this.params = new LinkedHashMap<>(params == null ? Map.of() : params); return this; }
  public Query withParam(String name, Object value) { this.params.put(name, value); return this; }
  public Object param(String name) {
    if (!params.containsKey(name)) throw new IllegalArgumentException("Missing query param: " + name);
    return params.get(name);
  }

  @Override
  public <Q> Q accept(QueryVisitor<Q> visitor) {
    return filter != null ? filter.accept(visitor) : null;
  }

  public static Query of(QueryElement filter) {
    return new Query().withFilter(filter);
  }

  public static Query and(QueryElement... elements) {
    return Query.of(QueryFilters.and(elements));
  }

  public static Query or(QueryElement... elements) {
    return Query.of(QueryFilters.or(elements));
  }
}

