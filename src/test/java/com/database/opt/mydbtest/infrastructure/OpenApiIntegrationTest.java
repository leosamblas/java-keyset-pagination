package com.database.opt.mydbtest.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OpenApiIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("GET /v3/api-docs deve retornar JSON do OpenAPI 3.0 com metadados e rotas documentadas")
    void shouldReturnOpenApiDocumentationJson() {
        webTestClient.get()
                .uri("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openapi").exists()
                .jsonPath("$.info.title").isEqualTo("API de Gestão de Recebíveis de Cartão")
                .jsonPath("$.info.version").isEqualTo("1.0.0")
                .jsonPath("$.paths['/api/v1/receivables']").exists()
                .jsonPath("$.paths['/api/v1/receivables'].post.summary").isEqualTo("Cadastra um recebível individual")
                .jsonPath("$.paths['/api/v1/receivables'].get.summary").isEqualTo("Consulta paginada via Keyset (Seek) Pagination")
                .jsonPath("$.paths['/api/v1/receivables/{id}'].get.summary").isEqualTo("Busca recebível por ID")
                .jsonPath("$.paths['/api/v1/receivables/batch'].post.summary").isEqualTo("Cadastra recebíveis em lote (Batch)");
    }

    @Test
    @DisplayName("GET /swagger-ui.html deve redirecionar para a interface web do Swagger UI")
    void shouldRedirectSwaggerUiHtml() {
        webTestClient.get()
                .uri("/swagger-ui.html")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(
                        HttpStatus.FOUND.value(),
                        HttpStatus.SEE_OTHER.value(),
                        HttpStatus.PERMANENT_REDIRECT.value(),
                        HttpStatus.TEMPORARY_REDIRECT.value(),
                        HttpStatus.OK.value()
                ))
                .expectHeader().value("Location", location -> assertThat(location).contains("swagger-ui"));
    }

    @Test
    @DisplayName("GET /webjars/swagger-ui/index.html deve responder com página HTML do Swagger UI")
    void shouldServeSwaggerUiWebJarPage() {
        byte[] htmlContent = webTestClient.get()
                .uri("/webjars/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(htmlContent).isNotNull();
        String htmlString = new String(htmlContent);
        assertThat(htmlString).contains("swagger-ui");
    }
}
