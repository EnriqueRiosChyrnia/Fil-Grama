package com.filgrama.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.filgrama.account.dto.ConnectResponse;
import com.filgrama.domain.Client;
import com.filgrama.domain.Post;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.Platform;

/**
 * TAREA A end-to-end: tras un callback OAuth exitoso, el track Sync dispara un escaneo SOLO de esa
 * cuenta (AFTER_COMMIT, async en el pool del job). Prueba la conexión real connect → callback con el
 * {@code MockOAuthProvider}/{@code MockInsightsProvider} y espera (poll) a que aparezcan los posts.
 */
class ConnectScanIntegrationTest extends SyncTestSupport {

    @Test
    void callbackExitoso_disparaScanInmediatoDeEsaCuenta() throws Exception {
        Client client = newClient("America/Asuncion");
        String admin = adminToken();

        // 1) connect → state firmado.
        ResponseEntity<ConnectResponse> connect = post(
                "/api/v1/clients/" + client.getId() + "/accounts/connect/tiktok", null, admin, ConnectResponse.class);
        assertThat(connect.getStatusCode().value()).isEqualTo(200);
        String state = connect.getBody().state();

        // 2) callback de la red (permitAll, navegador): canjea y crea la cuenta.
        String callback = "/api/v1/oauth/callback/tiktok?code=good-code&state="
                + URLEncoder.encode(state, StandardCharsets.UTF_8);
        ResponseEntity<Void> cb = get(callback, null, Void.class);
        assertThat(cb.getStatusCode().is3xxRedirection()).isTrue();

        // La cuenta quedó CONNECTED (el connect no se rompe pase lo que pase con el scan).
        SocialAccount account = awaitAccount(client.getId());
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CONNECTED);
        assertThat(account.getPlatform()).isEqualTo(Platform.TIKTOK);

        // 3) el scan async trajo los posts de ESA cuenta (TikTok mock = 2 videos).
        List<Post> posts = awaitPosts(account.getId(), 2);
        assertThat(posts).hasSize(2);
        // y la corrida de 1 cuenta quedó registrada.
        assertThat(syncRunRepository.count()).isPositive();
    }

    private SocialAccount awaitAccount(Long clientId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            List<SocialAccount> found = socialAccountRepository.findByClientId(clientId);
            if (!found.isEmpty()) {
                return found.get(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("La cuenta no se creó tras el callback");
    }

    private List<Post> awaitPosts(Long accountId, int expected) throws InterruptedException {
        for (int i = 0; i < 150; i++) { // hasta ~15s: el scan corre async en el pool del job
            List<Post> posts = postRepository.findByAccountId(accountId);
            if (posts.size() >= expected) {
                return posts;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("El scan al conectar no trajo los posts a tiempo");
    }
}
