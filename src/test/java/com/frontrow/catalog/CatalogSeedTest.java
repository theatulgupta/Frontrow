package com.frontrow.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.frontrow.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class CatalogSeedTest extends IntegrationTestBase {

    @Test
    void seedsOneShowWithAFrontRowAndAvailableInventory() throws Exception {
        Integer frontRow = jdbc.queryForObject("SELECT count(*) FROM seats WHERE front_row", Integer.class);
        Integer available = jdbc.queryForObject(
                "SELECT count(*) FROM seat_inventory WHERE status = 'AVAILABLE'",
                Integer.class);
        assertThat(frontRow).isEqualTo(12);
        assertThat(available).isEqualTo(216);

        HttpResult shows = get("/api/shows");
        assertThat(shows.status()).isEqualTo(200);
        assertThat(shows.body()).contains("Opening Night");

        HttpResult show = get("/api/shows/" + SHOW_ID);
        assertThat(show.status()).isEqualTo(200);
        assertThat(show.body()).contains("15000").contains("Frontrow Hall");

        HttpResult seats = get("/api/shows/" + SHOW_ID + "/seats");
        assertThat(seats.status()).isEqualTo(200);
        assertThat(seats.body()).contains("\"frontRow\":true").contains("AVAILABLE");
    }
}
