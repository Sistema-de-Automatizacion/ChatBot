package com.chatbot.motosdelcaribe.service;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatbot.motosdelcaribe.model.ChatSession;
import com.chatbot.motosdelcaribe.model.ChatSession.Step;
import com.chatbot.motosdelcaribe.model.Client;
import com.chatbot.motosdelcaribe.respository.ChatSessionRepository;
import com.chatbot.motosdelcaribe.respository.ClientRepository;
import com.chatbot.motosdelcaribe.util.PhoneNormalizer;

import lombok.RequiredArgsConstructor;

/**
 * Maquina de estados del bot. Toma (telefono entrante, texto del usuario) y
 * decide la respuesta + la transicion de sesion.
 *
 * Reglas:
 *   IDLE             + cualquier texto  -> si TELULT no matchea: rechazo (no hay sesion).
 *                                          si matchea: saludo + AWAITING_PLATE.
 *   AWAITING_PLATE   + placa correcta   -> menu + AWAITING_OPTION (guarda contractId).
 *                                          placa incorrecta: pide otra vez.
 *   AWAITING_OPTION  + "1"|"2"|"3"      -> respuesta y sesion borrada (vuelve a IDLE).
 *                                          opcion invalida: pide otra vez.
 *
 * Importante: el bot NUNCA calcula plata. Los importes salen tal cual de la BD;
 * el LLM o cualquier futura logica de NLU debe limitarse a clasificar intencion,
 * jamas a generar montos.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final NumberFormat MONEY = currencyFormatter();

    /**
     * La BD guarda los importes "sin fraccion de mil" (saldo, cuota, deuda):
     * el valor 540 representa $540.000 COP. Multiplicamos por este factor antes
     * de formatear para que el cliente vea el monto real.
     */
    private static final double STORAGE_TO_PESOS = 1_000d;

    /**
     * Menu mostrado tras confirmar la placa y tras una opcion invalida. Se
     * incluye una breve descripcion de cada opcion para que el cliente sepa
     * que va a recibir antes de elegir.
     */
    private static final String MENU =
        "Que deseas consultar?\n"
            + "1. Saldo pendiente — total que aun debes por la moto.\n"
            + "2. Proxima cuota — monto de tu siguiente pago semanal y dia en que vence.\n"
            + "3. Mora — si tienes cuotas atrasadas por pagar.\n\n"
            + "Por favor responde con el numero de la opcion (1, 2 o 3).";

    private final ClientRepository clientRepo;
    private final ChatSessionRepository sessionRepo;

    public record Reply(String text) {}

    @Transactional
    public Reply handle(String fromPhone, String userText) {
        String phone = PhoneNormalizer.last10Digits(fromPhone);
        String text = userText == null ? "" : userText.trim();

        if (phone.length() != 10) {
            return new Reply("No pudimos identificar tu numero. Por favor escribe desde un celular registrado.");
        }

        Optional<ChatSession> sessionOpt = sessionRepo.findById(phone);

        if (sessionOpt.isEmpty()) {
            return startConversation(phone);
        }

        ChatSession session = sessionOpt.get();
        return switch (session.getStep()) {
            case AWAITING_PLATE  -> handlePlate(session, text);
            case AWAITING_OPTION -> handleOption(session, text);
        };
    }

    // --- Steps ----------------------------------------------------------

    private Reply startConversation(String phone) {
        List<Client> matches = clientRepo.findByPhone(phone);
        if (matches.isEmpty()) {
            return new Reply("No tienes acceso a esta informacion desde este numero de telefono.");
        }
        String name = firstName(matches.get(0).getName());
        ChatSession session = new ChatSession(phone, Step.AWAITING_PLATE, null, Instant.now());
        sessionRepo.save(session);
        return new Reply("Hola " + name + ", soy el asistente de Motos del Caribe. "
            + "Por favor escribe la placa de tu moto para continuar.");
    }

    private Reply handlePlate(ChatSession session, String text) {
        String plate = normalizePlate(text);
        Optional<Client> match = clientRepo.findByPhoneAndNumPlate(session.getPhone(), plate);
        session.setLastSeenAt(Instant.now());

        if (match.isEmpty()) {
            sessionRepo.save(session);
            return new Reply("Esa placa no coincide con un contrato a tu nombre. "
                + "Verifica e intenta de nuevo.");
        }

        session.setContractId(match.get().getContrato());
        session.setStep(Step.AWAITING_OPTION);
        sessionRepo.save(session);
        return new Reply("Perfecto. " + MENU);
    }

    private Reply handleOption(ChatSession session, String text) {
        String choice = text.trim();
        if (!choice.equals("1") && !choice.equals("2") && !choice.equals("3")) {
            session.setLastSeenAt(Instant.now());
            sessionRepo.save(session);
            return new Reply("Opcion invalida. " + MENU);
        }

        Client client = clientRepo.findById(session.getContractId())
            .orElseThrow(() -> new IllegalStateException(
                "ChatSession con contractId no encontrado en la BD: " + session.getContractId()));

        Reply reply = switch (choice) {
            case "1" -> new Reply("Tu saldo pendiente por pagar es de "
                + formatMoney(client.getBalance()) + ".");
            case "2" -> new Reply("Tu proxima cuota es de "
                + formatMoney(client.getPayment())
                + " y vence el dia " + safeDay(client.getPaymentDay()) + ".");
            case "3" -> new Reply(formatMoraMessage(client));
            default  -> throw new IllegalStateException("unreachable");
        };

        // Conversacion completada: la sesion ya cumplio su funcion.
        sessionRepo.deleteById(session.getPhone());
        return reply;
    }

    // --- Helpers --------------------------------------------------------

    private static String formatMoraMessage(Client client) {
        double mora = client.getAcummulatedDebet();
        if (mora <= 0) {
            return "No tienes mora pendiente. Estas al dia con tu contrato.";
        }
        return "Tienes una mora pendiente de " + formatMoney(mora)
            + ". Por favor ponte al dia para evitar intereses adicionales.";
    }

    /**
     * Formatea un valor de la BD a moneda colombiana, aplicando el factor
     * de escala (la BD guarda 540 cuando el monto real son $540.000).
     */
    private static String formatMoney(double rawFromDb) {
        return MONEY.format(rawFromDb * STORAGE_TO_PESOS);
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    private static String normalizePlate(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static String safeDay(String dia) {
        return dia == null || dia.isBlank() ? "acordado" : dia;
    }

    private static NumberFormat currencyFormatter() {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        f.setMaximumFractionDigits(0);
        return f;
    }
}
