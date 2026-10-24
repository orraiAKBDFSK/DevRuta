package app.plataforma.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ApiApplicationTests extends IntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void contextLoads() {
	}

	@Test
	void flywayAppliesMigrationsOnRealPostgres() {
		Integer applied = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where success", Integer.class);

		assertThat(applied).isGreaterThanOrEqualTo(1);
	}

}
