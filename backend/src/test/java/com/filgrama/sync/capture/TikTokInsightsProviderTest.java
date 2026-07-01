package com.filgrama.sync.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.domain.enums.PostType;
import com.filgrama.sync.capture.dto.AccountCapture;
import com.filgrama.sync.capture.dto.PostInsightsCapture;
import com.filgrama.sync.capture.dto.PostsListCapture;
import com.filgrama.sync.capture.dto.RawPost;

/** Tests del provider real de TikTok con respuestas del Display API mockeadas a nivel HTTP. */
class TikTokInsightsProviderTest {

    private static final String BASE = "https://open.tiktokapis.com";
    private static final String TOKEN = "tt-access";

    private MockRestServiceServer server;

    private TikTokInsightsProvider provider() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        this.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        return new TikTokInsightsProvider(builder);
    }

    private static SocialAccount account() {
        SocialAccount a = new SocialAccount();
        a.setPlatform(Platform.TIKTOK);
        a.setExternalAccountId("open-id-1");
        return a;
    }

    @Test
    void accountInsightsMapsStats() {
        TikTokInsightsProvider p = provider();
        server.expect(requestTo(containsString("/v2/user/info/")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":{"user":{"follower_count":1000,"likes_count":5000,"video_count":42}},
                         "error":{"code":"ok","message":"","log_id":"x"}}""",
                        MediaType.APPLICATION_JSON));

        AccountCapture cap = p.fetchAccountInsights(account(), TOKEN, null, null);

        assertThat(cap.metrics().get("tt_follower_count")).isEqualByComparingTo("1000");
        assertThat(cap.metrics().get("tt_likes_count")).isEqualByComparingTo("5000");
        assertThat(cap.metrics().get("tt_video_count")).isEqualByComparingTo("42");
        server.verify();
    }

    @Test
    void videoListMapsToRawPosts() {
        TikTokInsightsProvider p = provider();
        server.expect(requestTo(containsString("/v2/video/list/")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":{"videos":[
                          {"id":"V1","title":"clip","share_url":"https://tt/V1","cover_image_url":"https://cdn/V1.jpg",
                           "create_time":1717243200,"view_count":100,"like_count":10,"comment_count":2,"share_count":1}
                         ],"cursor":0,"has_more":false},"error":{"code":"ok"}}""",
                        MediaType.APPLICATION_JSON));

        PostsListCapture cap = p.fetchPosts(account(), TOKEN);

        assertThat(cap.posts()).hasSize(1);
        RawPost post = cap.posts().get(0);
        assertThat(post.externalPostId()).isEqualTo("V1");
        assertThat(post.postType()).isEqualTo(PostType.VIDEO);
        assertThat(post.permalink()).isEqualTo("https://tt/V1");
        assertThat(post.publishedAt()).isNotNull();
        server.verify();
    }

    @Test
    void videoQueryMapsPostMetrics() {
        TikTokInsightsProvider p = provider();
        server.expect(requestTo(containsString("/v2/video/query/")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":{"videos":[{"id":"V1","view_count":100,"like_count":10,
                          "comment_count":2,"share_count":1}]},"error":{"code":"ok"}}""",
                        MediaType.APPLICATION_JSON));

        RawPost post = new RawPost("V1", PostType.VIDEO, null, null, null, null, null, false, null);
        PostInsightsCapture cap = p.fetchPostInsights(account(), post, TOKEN);

        assertThat(cap.metrics().get("tt_view_count")).isEqualByComparingTo("100");
        assertThat(cap.metrics().get("tt_like_count")).isEqualByComparingTo("10");
        assertThat(cap.metrics().get("tt_comment_count")).isEqualByComparingTo("2");
        assertThat(cap.metrics().get("tt_share_count")).isEqualByComparingTo("1");
        server.verify();
    }

    @Test
    void errorEnvelopeIsTerminal() {
        TikTokInsightsProvider p = provider();
        server.expect(requestTo(containsString("/v2/user/info/")))
                .andRespond(withSuccess("{\"data\":{},\"error\":{\"code\":\"access_token_invalid\","
                        + "\"message\":\"bad\",\"log_id\":\"x\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> p.fetchAccountInsights(account(), TOKEN, null, null))
                .isInstanceOf(InsightsException.class)
                .isNotInstanceOf(TransientInsightsException.class);
    }

    @Test
    void rateLimitEnvelopeIsTransient() {
        TikTokInsightsProvider p = provider();
        server.expect(requestTo(containsString("/v2/user/info/")))
                .andRespond(withSuccess("{\"data\":{},\"error\":{\"code\":\"rate_limit_exceeded\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> p.fetchAccountInsights(account(), TOKEN, null, null))
                .isInstanceOf(TransientInsightsException.class);
    }
}
