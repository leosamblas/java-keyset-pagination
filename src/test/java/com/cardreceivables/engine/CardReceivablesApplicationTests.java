package com.cardreceivables.engine;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CardReceivablesApplicationTests {

	@Autowired(required = false)
	private Flyway flyway;

	@Test
	@DisplayName("Deveria carregar o contexto Spring e executar migração V1 do Flyway")
	void contextLoads() {
		assertThat(flyway).isNotNull();
		MigrationInfo current = flyway.info().current();
		assertThat(current).isNotNull();
		assertThat(current.getVersion().getVersion()).isEqualTo("1");
		assertThat(current.getDescription()).isEqualTo("create receivables table");
	}

}
