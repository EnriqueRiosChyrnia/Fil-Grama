package com.filgrama.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** CV1: el ciclo de vida (spec/09) suma el estado de baja {@code REMOVED}. */
class AccountStatusTest {

    @Test
    void tieneEstadoRemoved() {
        assertThat(AccountStatus.valueOf("REMOVED")).isEqualTo(AccountStatus.REMOVED);
    }
}
