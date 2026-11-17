package br.com.snackflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final String SCHEMA = "database/lanchonete.sql"; // ajuste se o nome for outro

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("lanchonete")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(SCHEMA),
                        "/docker-entrypoint-initdb.d/lanchonete.sql");
    }
}