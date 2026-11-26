package br.com.snackflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Sobe um único MySQL 8.4 (com database/lanchonete.sql) e o reaproveita em
 * todas as classes de teste da execução (padrão singleton container).
 * Os dados de exemplo do script estão disponíveis; testes de integração devem
 * usar dados próprios ou limpeza para não depender deles.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final String SCHEMA = "database/lanchonete.sql";

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("lanchonete")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(SCHEMA),
                    "/docker-entrypoint-initdb.d/lanchonete.sql");

    static {
        MYSQL.start();
    }

    @Bean
    @ServiceConnection
    public MySQLContainer mysqlContainer() {
        return MYSQL;
    }
}
