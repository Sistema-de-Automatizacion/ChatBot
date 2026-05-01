package com.chatbot.motosdelcaribe.util;

/**
 * Normalizacion de telefonos a la forma "10 digitos sin codigo de pais", que es
 * como la columna TELULT de la vista de cartera los guarda. Meta WhatsApp Cloud
 * API entrega los telefonos en formato E.164 sin el "+" (ej. 573041234567);
 * el matching se hace siempre comparando los ultimos 10 digitos.
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {}

    /**
     * Devuelve los ultimos 10 digitos de la entrada, ignorando cualquier caracter
     * no numerico. Si hay menos de 10 digitos, devuelve todos los que haya.
     * Nunca lanza; entradas null o vacias devuelven cadena vacia.
     */
    public static String last10Digits(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() <= 10) return digits;
        return digits.substring(digits.length() - 10);
    }
}
