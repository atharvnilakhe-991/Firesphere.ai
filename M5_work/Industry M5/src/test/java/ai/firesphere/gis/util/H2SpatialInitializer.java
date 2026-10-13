package ai.firesphere.gis.util;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Initializes spatial SQL aliases in H2 in-memory test database,
 * allowing PostgreSQL/PostGIS spatial functions (ST_DISTANCE, ST_CONTAINS, ST_INTERSECTS)
 * to be executed seamlessly during tests using JTS under the hood.
 */
@Component
@Profile("test")
public class H2SpatialInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public H2SpatialInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS ST_DISTANCE FOR \"ai.firesphere.gis.util.H2SpatialFunctions.stDistance\"");
            jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS ST_CONTAINS FOR \"ai.firesphere.gis.util.H2SpatialFunctions.stContains\"");
            jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS ST_INTERSECTS FOR \"ai.firesphere.gis.util.H2SpatialFunctions.stIntersects\"");
            jdbcTemplate.execute("CREATE ALIAS IF NOT EXISTS ST_DWITHIN FOR \"ai.firesphere.gis.util.H2SpatialFunctions.stDWithin\"");
        } catch (Exception ignored) {
        }
    }
}
