package com.filgrama.sync.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.domain.enums.PostType;
import com.filgrama.sync.capture.dto.AccountCapture;
import com.filgrama.sync.capture.dto.AudienceDemographicsCapture;
import com.filgrama.sync.capture.dto.DemographicSegment;
import com.filgrama.sync.capture.dto.PostInsightsCapture;
import com.filgrama.sync.capture.dto.RawPost;

/**
 * Parseo de los payloads v1.1 (FG-T1) del provider de Meta con respuestas Graph mockeadas a nivel HTTP:
 * demografía de seguidores (breakdowns), splits {@code follow_type} de views/reach, {@code profile_views},
 * taps por destino, y métricas extra por media. Cubre además la degradación elegante (best-effort).
 */
class MetaInsightsProviderV11Test {

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
    void followerDemographicsParsesSegmentsPerBreakdown() {
        MetaInsightsProvider p = provider();
        server.expect(requestTo(containsString("breakdown=age")))
                .andRespond(withSuccess("""
                        {"data":[{"name":"follower_demographics","period":"lifetime","total_value":{"breakdowns":[
                          {"dimension_keys":["age"],"results":[
                            {"dimension_values":["25-34"],"value":40},
                            {"dimension_values":["35-44"],"value":25}]}]}}]}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("breakdown=gender")))
                .andRespond(withSuccess("""
                        {"data":[{"name":"follower_demographics","total_value":{"breakdowns":[
                          {"dimension_keys":["gender"],"results":[
                            {"dimension_values":["F"],"value":80},
                            {"dimension_values":["M"],"value":57}]}]}}]}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("breakdown=city")))
                .andRespond(withSuccess("""
                        {"data":[{"name":"follower_demographics","total_value":{"breakdowns":[
                          {"dimension_keys":["city"],"results":[
                            {"dimension_values":["Encarnación, Itapúa"],"value":60}]}]}}]}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("breakdown=country")))
                .andRespond(withSuccess("""
                        {"data":[{"name":"follower_demographics","total_value":{"breakdowns":[
                          {"dimension_keys":["country"],"results":[
                            {"dimension_values":["PY"],"value":120},
                            {"dimension_values":["AR"],"value":17}]}]}}]}""", MediaType.APPLICATION_JSON));

        AudienceDemographicsCapture cap = p.fetchAudienceDemographics(account(Platform.INSTAGRAM, "IG123"), TOKEN);

        assertThat(cap.segments()).hasSize(7);
        assertThat(cap.segments()).allMatch(s -> "FOLLOWER".equals(s.scope()));
        assertThat(cap.segments()).contains(
                new DemographicSegment("FOLLOWER", "AGE", "25-34", new java.math.BigDecimal("40")),
                new DemographicSegment("FOLLOWER", "GENDER", "F", new java.math.BigDecimal("80")),
                new DemographicSegment("FOLLOWER", "COUNTRY", "PY", new java.math.BigDecimal("120")));
        assertThat(cap.segments()).anyMatch(s -> "CITY".equals(s.breakdownType())
                && s.breakdownValue().startsWith("Encarnación"));
        assertThat(cap.rawJson()).contains("follower_demographics_age").contains("follower_demographics_country");
        server.verify();
    }

    @Test
    void accountExtrasParsesFollowTypeSplitsProfileViewsAndTaps() {
        MetaInsightsProvider p = provider();
        server.expect(requestTo(containsString("metric=profile_views")))
                .andRespond(withSuccess("{\"data\":[{\"name\":\"profile_views\",\"total_value\":{\"value\":363}}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("metric=views&period")))
                .andRespond(withSuccess("""
                        {"data":[{"name":"views","total_value":{"breakdowns":[
                          {"dimension_keys":["follow_type"],"results":[
                            {"dimension_values":["FOLLOWER"],"value":3600},
                            {"dimension_values":["NON_FOLLOWER"],"value":1500}]}]}}]}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("metric=reach&period")))
                .andRespond(withSuccess("""
                        {"data":[{"name":"reach","total_value":{"breakdowns":[
                          {"dimension_keys":["follow_type"],"results":[
                            {"dimension_values":["FOLLOWER"],"value":1200},
                            {"dimension_values":["NON_FOLLOWER"],"value":584}]}]}}]}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("metric=profile_links_taps")))
                .andRespond(withSuccess("""
                        {"data":[{"name":"profile_links_taps","total_value":{"breakdowns":[
                          {"dimension_keys":["contact_button_type"],"results":[
                            {"dimension_values":["WHATSAPP"],"value":8},
                            {"dimension_values":["DIRECTION"],"value":2},
                            {"dimension_values":["CALL"],"value":1}]}]}}]}""", MediaType.APPLICATION_JSON));

        AccountCapture cap = p.fetchAccountExtras(account(Platform.INSTAGRAM, "IG123"), TOKEN);

        assertThat(cap.metrics().get("ig_profile_views")).isEqualByComparingTo("363");
        assertThat(cap.metrics().get("ig_views_followers")).isEqualByComparingTo("3600");
        assertThat(cap.metrics().get("ig_views_non_followers")).isEqualByComparingTo("1500");
        assertThat(cap.metrics().get("ig_reach_followers")).isEqualByComparingTo("1200");
        assertThat(cap.metrics().get("ig_reach_non_followers")).isEqualByComparingTo("584");
        assertThat(cap.metrics().get("ig_taps_whatsapp")).isEqualByComparingTo("8");
        assertThat(cap.metrics().get("ig_taps_direction")).isEqualByComparingTo("2");
        assertThat(cap.metrics().get("ig_profile_links_taps")).isEqualByComparingTo("11"); // 8+2+1
        server.verify();
    }

    @Test
    void postExtrasParsesRepostsProfileVisitsAndWatchTimeMsToSeconds() {
        MetaInsightsProvider p = provider();
        server.expect(requestTo(containsString("metric=reposts,profile_visits,ig_reels_avg_watch_time")))
                .andRespond(withSuccess("""
                        {"data":[
                          {"name":"reposts","total_value":{"value":12}},
                          {"name":"profile_visits","total_value":{"value":7}},
                          {"name":"ig_reels_avg_watch_time","total_value":{"value":13000}}]}""",
                        MediaType.APPLICATION_JSON));

        RawPost reel = new RawPost("M2", PostType.REEL, null, null, null, null, null, false, null);
        PostInsightsCapture cap = p.fetchPostExtras(account(Platform.INSTAGRAM, "IG123"), reel, TOKEN);

        assertThat(cap.metrics().get("ig_post_reposts")).isEqualByComparingTo("12");
        assertThat(cap.metrics().get("ig_post_profile_visits")).isEqualByComparingTo("7");
        assertThat(cap.metrics().get("ig_reels_avg_watch_time")).isEqualByComparingTo("13"); // 13000 ms → 13 s
        server.verify();
    }

    @Test
    void postExtrasOmitsWatchTimeForNonReel() {
        MetaInsightsProvider p = provider();
        server.expect(requestTo(containsString("metric=reposts,profile_visits&")))
                .andRespond(withSuccess("{\"data\":[{\"name\":\"reposts\",\"total_value\":{\"value\":3}}]}",
                        MediaType.APPLICATION_JSON));

        RawPost feed = new RawPost("M1", PostType.IMAGE, null, null, null, null, null, false, null);
        PostInsightsCapture cap = p.fetchPostExtras(account(Platform.INSTAGRAM, "IG123"), feed, TOKEN);

        assertThat(cap.metrics()).containsKey("ig_post_reposts");
        assertThat(cap.metrics()).doesNotContainKey("ig_reels_avg_watch_time");
        server.verify();
    }

    @Test
    void extrasAreBestEffortAndDoNotThrowOnApiError() {
        MetaInsightsProvider p = provider();
        // Cualquier 4xx en las sub-llamadas debe degradar a vacío, nunca propagar (no rompe la captura CORE).
        server.expect(ExpectedCount.manyTimes(), method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        SocialAccount acct = account(Platform.INSTAGRAM, "IG123");
        assertThatCode(() -> {
            AccountCapture extras = p.fetchAccountExtras(acct, TOKEN);
            AudienceDemographicsCapture demo = p.fetchAudienceDemographics(acct, TOKEN);
            assertThat(extras.metrics()).isEmpty();
            assertThat(demo.segments()).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void extrasEmptyForNonInstagram() {
        MetaInsightsProvider p = provider();
        SocialAccount fb = account(Platform.FACEBOOK, "PAGE9");

        assertThat(p.fetchAccountExtras(fb, TOKEN).metrics()).isEmpty();
        assertThat(p.fetchAudienceDemographics(fb, TOKEN).segments()).isEmpty();
        List<DemographicSegment> none = p.fetchAudienceDemographics(fb, TOKEN).segments();
        assertThat(none).isEmpty();
        server.verify(); // ninguna llamada HTTP para FB
    }
}
