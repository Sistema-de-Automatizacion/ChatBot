package com.chatbot.motosdelcaribe.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNormalizerTest {

    @Test
    void devuelveCadenaVaciaParaNull() {
        assertThat(PhoneNormalizer.last10Digits(null)).isEmpty();
    }

    @Test
    void devuelveCadenaVaciaParaSinDigitos() {
        assertThat(PhoneNormalizer.last10Digits("hola")).isEmpty();
        assertThat(PhoneNormalizer.last10Digits("")).isEmpty();
    }

    @Test
    void quitaCodigoDePaisDeE164() {
        // Meta entrega 12 digitos (57 + 10 del celular).
        assertThat(PhoneNormalizer.last10Digits("573041234567")).isEqualTo("3041234567");
    }

    @Test
    void quitaSignoMasYEspacios() {
        assertThat(PhoneNormalizer.last10Digits("+57 304 123 4567")).isEqualTo("3041234567");
        assertThat(PhoneNormalizer.last10Digits("+57-304-123-4567")).isEqualTo("3041234567");
    }

    @Test
    void preservaTelefonoYaNormalizado() {
        assertThat(PhoneNormalizer.last10Digits("3041234567")).isEqualTo("3041234567");
    }

    @Test
    void devuelveTodosLosDigitosCuandoHayMenosDeDiez() {
        assertThat(PhoneNormalizer.last10Digits("12345")).isEqualTo("12345");
    }
}
