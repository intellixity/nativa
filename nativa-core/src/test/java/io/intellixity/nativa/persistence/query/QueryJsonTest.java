package io.intellixity.nativa.persistence.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class QueryJsonTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void parsesNotNode() throws Exception {
    String s = """
        {
          "filter": {
            "not": { "eq": { "field": "tenantId", "value": "tenant-1" } }
          }
        }
        """;
    Query q = JSON.readValue(s, Query.class);
    assertNotNull(q.filter());
    assertTrue(q.filter() instanceof NotElement);
    NotElement n = (NotElement) q.filter();
    assertTrue(n.element() instanceof Condition);
    Condition c = (Condition) n.element();
    assertEquals("tenantId", c.property());
    assertEquals(Operator.EQ, c.operator());
    assertEquals("tenant-1", c.value());
  }

  @Test
  void backCompatGroupMissingClauseDefaultsToAnd() throws Exception {
    String s = """
        {
          "filter": {
            "elements": [
              { "operator": "EQ", "property": "tenantId", "value": "tenant-1" }
            ]
          }
        }
        """;
    Query q = JSON.readValue(s, Query.class);
    assertNotNull(q.filter());
    assertTrue(q.filter() instanceof LogicalGroup);
    LogicalGroup g = (LogicalGroup) q.filter();
    assertEquals(Clause.AND, g.clause());
    assertEquals(1, g.elements().size());
  }
}



