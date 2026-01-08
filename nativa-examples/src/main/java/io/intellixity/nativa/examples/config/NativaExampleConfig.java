package io.intellixity.nativa.examples.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.intellixity.nativa.examples.engine.Engines;
import io.intellixity.nativa.persistence.authoring.AuthoringRegistry;
import io.intellixity.nativa.persistence.authoring.InMemoryAuthoringRegistry;
import io.intellixity.nativa.persistence.authoring.yaml.YamlEntityAuthoringLoader;
import io.intellixity.nativa.persistence.dmlast.DmlPlanner;
import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.exec.handle.EngineHandle;
import io.intellixity.nativa.persistence.governance.*;
import io.intellixity.nativa.persistence.jdbc.JdbcDataEngine;
import io.intellixity.nativa.persistence.jdbc.JdbcHandle;
import io.intellixity.nativa.persistence.jdbc.dialect.JdbcDialect;
import io.intellixity.nativa.persistence.jdbc.dml.JdbcDmlPlanner;
import io.intellixity.nativa.persistence.jdbc.postgres.PostgresDialect;
import io.intellixity.nativa.persistence.pojo.PojoAccessorRegistry;
import io.intellixity.nativa.persistence.pojo.PojoMutatorRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableConfigurationProperties(TenantsProperties.class)
public class NativaExampleConfig {

  @Bean
  public AuthoringRegistry authoringRegistry() throws Exception {
    // Load YAML authoring from resources.
    YamlEntityAuthoringLoader loader = new YamlEntityAuthoringLoader();
    Path authoringDir = findAuthoringDir();
    List<io.intellixity.nativa.persistence.authoring.EntityAuthoring> entities = loader.loadDir(authoringDir);
    return new InMemoryAuthoringRegistry(entities);
  }

  private static Path findAuthoringDir() {
    String userDir = System.getProperty("user.dir");
    Path p1 = Path.of(userDir, "src", "main", "resources", "authoring"); // running from nativa-examples/
    if (p1.toFile().exists() && p1.toFile().isDirectory()) return p1;

    Path p2 = Path.of(userDir, "nativa-examples", "src", "main", "resources", "authoring"); // running from repo root
    if (p2.toFile().exists() && p2.toFile().isDirectory()) return p2;

    Path p3 = Path.of(userDir).resolve("..").normalize().resolve("nativa-examples").resolve("src").resolve("main").resolve("resources").resolve("authoring");
    if (p3.toFile().exists() && p3.toFile().isDirectory()) return p3;

    throw new IllegalStateException("Could not locate authoring directory. Tried: " + p1 + ", " + p2 + ", " + p3);
  }

  @Bean
  public PojoAccessorRegistry pojoAccessorRegistry() {
    return new io.intellixity.nativa.persistence.generated.GeneratedPojoAccessorRegistry();
  }

  @Bean
  public PojoMutatorRegistry pojoMutatorRegistry() {
    return new io.intellixity.nativa.persistence.generated.GeneratedPojoMutatorRegistry();
  }

  @Bean
  public EngineHandleResolver engineHandleResolver(TenantsProperties props) {
    Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    return new EngineHandleResolver() {
      @Override
      @SuppressWarnings("unchecked")
      public <T extends EngineHandle<?>> T resolve(String engineFamily, GovernanceContext ctx, boolean readOnly) {
        if (!"jdbc".equals(engineFamily)) {
          throw new IllegalArgumentException("Unsupported engineFamily for examples: " + engineFamily);
        }
        String tenantId = String.valueOf(ctx.getRequired("tenantId"));
        TenantsProperties.TenantDb db = props.getDb().get(tenantId);
        if (db == null) throw new IllegalArgumentException("Unknown tenantId: " + tenantId);

        String url = readOnly && db.getReadOnlyJdbcUrl() != null && !db.getReadOnlyJdbcUrl().isBlank()
            ? db.getReadOnlyJdbcUrl()
            : db.getJdbcUrl();
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Missing jdbcUrl for tenantId=" + tenantId);

        String poolKey = tenantId + "|" + (readOnly ? "ro" : "rw");
        HikariDataSource ds = pools.computeIfAbsent(poolKey, k -> {
          HikariConfig hc = new HikariConfig();
          hc.setJdbcUrl(url);
          hc.setUsername(db.getUsername());
          hc.setPassword(db.getPassword());
          hc.setMaximumPoolSize(10);
          return new HikariDataSource(hc);
        });

        // Tenant-per-database model: the physical store is isolated per tenant.
        JdbcHandle handle = new JdbcHandle("jdbc:" + poolKey, ds, db.getSchema(), true);
        return (T) handle;
      }
    };
  }

  @Bean
  public DataEngineFactory dataEngineFactory(AuthoringRegistry authoring,
                                             PojoAccessorRegistry accessors,
                                             PojoMutatorRegistry mutators) {
    JdbcDialect dialect = new PostgresDialect();
    // Example: tenant boundary is driven by the 'tenantId' context key.
    // Integrators can replace this with custom keys like 'x'/'y'.
    java.util.Set<String> tenantBoundaryKeys = java.util.Set.of("tenantId");
    DmlPlanner planner = new JdbcDmlPlanner(authoring, accessors, tenantBoundaryKeys);

    return (engineFamily, handle) -> {
      if (!"jdbc".equals(engineFamily)) throw new IllegalArgumentException("Unsupported engineFamily: " + engineFamily);
      Objects.requireNonNull(handle, "handle");
      JdbcHandle h = (JdbcHandle) handle;
      DataEngine<JdbcHandle> base = new JdbcDataEngine(h, authoring, dialect, planner, Propagation.REQUIRED);
      return new GovernedDataEngine<>(authoring, base, mutators);
    };
  }

  @Bean
  public GovernanceDataEngineResolver governanceDataEngineResolver(EngineHandleResolver handleResolver,
                                                                   DataEngineFactory factory) {
    // LRU+TTL cache: tune as needed
    return new GovernanceDataEngineResolver(handleResolver, factory, 100, 100, 10 * 60_000L, 5 * 60_000L);
  }

  @Bean
  public Engines engines(GovernanceDataEngineResolver resolver) {
    return new Engines(resolver);
  }
}


