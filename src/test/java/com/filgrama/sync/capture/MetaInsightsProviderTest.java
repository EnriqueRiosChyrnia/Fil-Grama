package com.filgrama.sync.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

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

/**
 * Tests del provider real de Meta con respuestas Graph mockeadas a nivel HTTP (sin red en CI):
 * verifica el parseo de payloads de ejemplo a {@code metric_key} CORE y la clasificación de errores.
 */
class MetaInsightsProviderTest {

    private static final String BASE = "https://graph.facebook.com";
    private static final String TOKEN = "page-token";

    private MockRestServiceServer server;

    private MetaInsightsProvider provider() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        this.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        return new MetaInsightsProvider(builder);
    }

    private static SocialAccount account(Platform platform, String externalId) {
        SocialAccount a = new SocialAccount();
        a.setPlatform(platform);
        a.setExternalAccountId(externalId);
        return a;
    }

    @Test
    void igAccountInsightsMapsCoreMetrics() {
        MetaInsightsProvider p = provider();
        server.expect(requestTo(containsString("/v21.0/IG123?fields=followers_count")))
                .andRespond(withSuccess("{\"followers_count\":5000,\"username\":\"demo\",\"id\":\"IG123\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v21.0/IG123/insights")))
                .andRespond(withSuccess("""
                        {"data":[
                          {"name":"reach","total_value":{"value":1000}},
                          {"name":"views","total_value":{"value":2000}},
                          {"name":"total_interactions","total_value":{"value":300}},
                          {"name":"accounts_engaged","total_value":{"value":150}}
                        ]}""", MediaType.APPLICATION_JSON));

        AccountCapture cap = p.fetchAccountInsights(account(Platform.INSTAGRAM, "IG123"), TOKEN);

        assertThat(cap.metrics()).containsKeys("ig_followers_count", "ig_reach", "ig_views",
                "ig_total_interactions", "ig_accounts_engaged");
        assertThat(cap.metrics().get("ig_followers_count")).isEqualByComparingTo("5000");
        assertThat(cap.metrics().get("ig_reach")).isEqualByComparingTo("1000");
        assertThat(cap.rawJson()).contains("\"node\":").contains("\"insights\":");
        server.verify();
    }

    @Test
    void fbAccountInsightsReadsValuesArray() {
        MetaInsightsProvider p = provider();
        server.expect(requestTo(containsString("/v21.0/PAGE9/insights")))
                .andRespond(withSuccess("""
                        {"data":[
                          {"name":"page_views_total","values":[{"value":10},{"value":12}]},
                          {"name":"page_post_engagements","values":[{"value":50}]},
                          {"name":"page_fan_adds","values":[{"value":7}]}
                        ]}""", MediaType.APPLICATION_JSON));

        AccountCapture cap = p.fetchAccountInsights(account(Platform.FACEBOOK, "PAGE9"), TOKEN);

        assertThat(cap.metrics().get("fb_page_views_total")).isEqualByComparingTo("12"); // último de la serie
        assertThat(cap.metrics().get("fb_page_post_engagements")).isEqualByComparingTo("50");
        assertThat(cap.metrics().get("fb_page_fan_adds")).isEqualByComparingTo("7");
        server.verify();
    }

    @Test
    void igPostsParsesTypesFromMediaProductType() {
        MetaInsightsProvider p = provider();
        server.expect(requestTo(containsString("/v21.0/IG123/media")))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":"M1","caption":"hi","media_type":"IMAGE","media_product_type":"FEED",
                           "permalink":"https://ig/M1","timestamp":"2026-06-01T12:00:00+0000"},
                          {"id":"M2","media_type":"VIDEO","media_product_type":"REELS",
                           "permalink":"https://ig/M2","timestamp":"2026-06-02T08:00:00+0000"}
                        ]}""", MediaType.APPLICATION_JSON));

        PostsListCapture cap = p.fetchPosts(account(Platform.INSTAGRAM, "IG123"), TOKEN);

        assertThat(cap.posts()).hasSize(2);
        assertThat(cap.posts().get(0).postType()).isEqualTo(PostType.IMAGE);
        assertThat(cap.posts().get(1).postType()).isEqualTo(PostType.REEL);
        assertThat(cap.posts().get(0).publishedAt()).isNotNull();
        server.verify();
    }

    @Test
    void igPostInsightsCombinesNodeFieldsAndInsights() {
        MetaInsightsProvider p = provider();
        server.expect(requestTo(containsString("/v21.0/M1?fields=like_count")))
                .andRespond(withSuccess("{\"like_count\":80,\"comments_count\":12,\"id\":\"M1\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v21.0/M1/insights")))
                .andRespond(withSuccess("""
                        {"data":[
                          {"name":"reach","values":[{"value":900}]},
                          {"name":"views","values":[{"value":1500}]},
                          {"name":"saved","values":[{"value":20}]},
                          {"name":"shares","values":[{"value":5}]},
                          {"name":"total_interactions","values":[{"value":117}]}
                        ]}""", MediaType.APPLICATION_JSON));

        RawPost post = new RawPost("M1", PostType.IMAGE, null, null, null, null, null, false, null);
        PostInsightsCapture cap = p.fetchPostInsights(account(Platform.INSTAGRAM, "IG123"), post, TOKEN);

        assertThat(cap.metrics().get("ig_post_likes")).isEqualByComparingTo("80");
        assertThat(cap.metrics().get("ig_post_comments")).isEqualByComparingTo("12");
        assertThat(cap.metrics().get("ig_post_reach")).isEqualByComparingTo("900");
        assertThat(cap.metrics().get("ig_post_total_interactions")).isEqualByComparingTo("117");
        server.verify();
    }

    @Test
    void igStoriesFetchMetricsAndMarkEphemeral() {
        MetaInsightsProvider p = provider();
        server.expect(requestTo(containsString("/v21.0/IG123/stories")))
                .andRespond(withSuccess("""
                        {"data":[{"id":"S1","media_type":"IMAGE","permalink":"https://ig/S1",
                          "thumbnail_url":"https://cdn/S1.jpg","timestamp":"2026-06-03T10:00:00+0000"}]}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v21.0/S1/insights")))
                .andRespond(withSuccess("{\"data\":[{\"name\":\"reach\",\"values\":[{\"value\":400}]},"
                        + "{\"name\":\"replies\",\"values\":[{\"value\":9}]}]}", MediaType.APPLICATION_JSON));

        List<com.filgrama.sync.capture.dto.StoryCapture> stories =
                p.fetchStories(account(Platform.INSTAGRAM, "IG123"), TOKEN);

        assertThat(stories).hasSize(1);
        assertThat(stories.get(0).meta().ephemeral()).isTrue();
        assertThat(stories.get(0).meta().expiresAt()).isNotNull();
        assertThat(stories.get(0).metrics().get("ig_story_reach")).isEqualByComparingTo("400");
        assertThat(stories.get(0).metrics().get("ig_story_replies")).isEqualByComparingTo("9");
        server.verify();
    }

    @Test
    void serverErrorIsTransient() {
        MetaInsightsProvider p = provider();
        server.expect(method(HttpMethod.GET)).andRespond(withServerError());

        assertThatThrownBy(() -> p.fetchAccountInsights(account(Platform.FACEBOOK, "PAGE9"), TOKEN))
                .isInstanceOf(TransientInsightsException.class);
    }

    @Test
    void tiktokNotSupported() {
        assertThat(provider().supports(Platform.TIKTOK)).isFalse();
    }
}
