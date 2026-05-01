package com.chatbot.motosdelcaribe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chatbot.motosdelcaribe.model.ChatSession;
import com.chatbot.motosdelcaribe.model.ChatSession.Step;
import com.chatbot.motosdelcaribe.model.Client;
import com.chatbot.motosdelcaribe.respository.ChatSessionRepository;
import com.chatbot.motosdelcaribe.respository.ClientRepository;
import com.chatbot.motosdelcaribe.service.ConversationService.Reply;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    private static final String FROM_E164 = "573041234567";
    private static final String PHONE     = "3041234567";

    @Mock private ClientRepository clientRepo;
    @Mock private ChatSessionRepository sessionRepo;
    @InjectMocks private ConversationService service;

    private Client sampleClient;

    @BeforeEach
    void setup() {
        sampleClient = new Client(
            "001", "XYJ56P", "Sebastian Perez",
            540.0,    // balance
            100.0,    // payment (cuota)
            0.0,      // acummulatedDebet
            PHONE,
            "AL DIA", // estado_semana
            "Mier"    // dia_canon
        );
    }

    // ---- IDLE -> rechazo ----

    @Test
    void siElTelefonoNoEstaEnTELULTRechazaSinCrearSesion() {
        when(sessionRepo.findById(PHONE)).thenReturn(Optional.empty());
        when(clientRepo.findByPhone(PHONE)).thenReturn(List.of());

        Reply reply = service.handle(FROM_E164, "Hola");

        assertThat(reply.text()).contains("No tienes acceso");
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void siElNumeroEntranteEsBasuraDevuelveErrorAmable() {
        Reply reply = service.handle("not-a-phone", "Hola");
        assertThat(reply.text()).contains("identificar tu numero");
        verify(sessionRepo, never()).save(any());
    }

    // ---- IDLE -> AWAITING_PLATE ----

    @Test
    void primerMensajeDeUnClienteValidoLoSaludaYAbreSesion() {
        when(sessionRepo.findById(PHONE)).thenReturn(Optional.empty());
        when(clientRepo.findByPhone(PHONE)).thenReturn(List.of(sampleClient));

        Reply reply = service.handle(FROM_E164, "buenos dias");

        assertThat(reply.text())
            .contains("Hola Sebastian")
            .contains("placa");

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionRepo).save(captor.capture());
        ChatSession created = captor.getValue();
        assertThat(created.getPhone()).isEqualTo(PHONE);
        assertThat(created.getStep()).isEqualTo(Step.AWAITING_PLATE);
        assertThat(created.getContractId()).isNull();
        assertThat(created.getLastSeenAt()).isNotNull();
    }

    // ---- AWAITING_PLATE ----

    @Test
    void placaCorrectaAvanzaAOpcionYGuardaContractId() {
        ChatSession session = new ChatSession(PHONE, Step.AWAITING_PLATE, null, hace(60));
        when(sessionRepo.findById(PHONE)).thenReturn(Optional.of(session));
        when(clientRepo.findByPhoneAndNumPlate(PHONE, "XYJ56P")).thenReturn(Optional.of(sampleClient));

        Reply reply = service.handle(FROM_E164, "  xyj56p  ");

        // El menu debe mostrar las 3 opciones con descripcion (no solo el numero).
        assertThat(reply.text())
            .contains("1. Saldo pendiente")
            .contains("2. Proxima cuota")
            .contains("3. Mora")
            .contains("siguiente pago"); // descripcion de la opcion 2
        assertThat(session.getStep()).isEqualTo(Step.AWAITING_OPTION);
        assertThat(session.getContractId()).isEqualTo("001");
        verify(sessionRepo).save(session);
    }

    @Test
    void placaIncorrectaPideOtraVezSinAvanzarDeStep() {
        ChatSession session = new ChatSession(PHONE, Step.AWAITING_PLATE, null, hace(60));
        when(sessionRepo.findById(PHONE)).thenReturn(Optional.of(session));
        when(clientRepo.findByPhoneAndNumPlate(PHONE, "ZZZ999")).thenReturn(Optional.empty());

        Reply reply = service.handle(FROM_E164, "ZZZ999");

        assertThat(reply.text()).contains("no coincide");
        assertThat(session.getStep()).isEqualTo(Step.AWAITING_PLATE);
        assertThat(session.getContractId()).isNull();
        verify(sessionRepo).save(session); // se guarda solo para refrescar lastSeenAt
    }

    // ---- AWAITING_OPTION ----

    @Test
    void opcion1RetornaSaldoMultiplicadoPor1000YBorraLaSesion() {
        // sampleClient.balance = 540 en BD -> debe mostrarse como $540.000.
        ChatSession session = new ChatSession(PHONE, Step.AWAITING_OPTION, "001", hace(30));
        when(sessionRepo.findById(PHONE)).thenReturn(Optional.of(session));
        when(clientRepo.findById("001")).thenReturn(Optional.of(sampleClient));

        Reply reply = service.handle(FROM_E164, "1");

        assertThat(reply.text())
            .contains("saldo pendiente")
            .contains("540.000");           // x1000 + separador es-CO
        assertThat(reply.text()).doesNotContain("$ 540 ");  // no debe quedar sin escalar
        verify(sessionRepo).deleteById(PHONE);
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void opcion2RetornaProximaCuotaConDiaYBorraSesion() {
        // sampleClient.payment = 100 en BD -> debe mostrarse como $100.000.
        ChatSession session = new ChatSession(PHONE, Step.AWAITING_OPTION, "001", hace(30));
        when(sessionRepo.findById(PHONE)).thenReturn(Optional.of(session));
        when(clientRepo.findById("001")).thenReturn(Optional.of(sampleClient));

        Reply reply = service.handle(FROM_E164, "2");

        assertThat(reply.text())
            .contains("proxima cuota")
            .contains("100.000")            // x1000 + separador es-CO
            .contains("Mier");
        verify(sessionRepo).deleteById(PHONE);
    }

    @Test
    void opcion3SinMoraRetornaMensajeDeAlDia() {
        sampleClient.setAcummulatedDebet(0.0);
        ChatSession session = new ChatSession(PHONE, Step.AWAITING_OPTION, "001", hace(30));
        when(sessionRepo.findById(PHONE)).thenReturn(Optional.of(session));
        when(clientRepo.findById("001")).thenReturn(Optional.of(sampleClient));

        Reply reply = service.handle(FROM_E164, "3");

        assertThat(reply.text())
            .contains("No tienes mora")
            .contains("al dia");
        verify(sessionRepo).deleteById(PHONE);
    }

    @Test
    void opcion3ConMoraRetornaMontoMultiplicadoPor1000YBorraSesion() {
        // BD guarda 75 (= $75.000); con el factor x1000 debe mostrarse "$ 75.000".
        sampleClient.setAcummulatedDebet(75.0);
        ChatSession session = new ChatSession(PHONE, Step.AWAITING_OPTION, "001", hace(30));
        when(sessionRepo.findById(PHONE)).thenReturn(Optional.of(session));
        when(clientRepo.findById("001")).thenReturn(Optional.of(sampleClient));

        Reply reply = service.handle(FROM_E164, "3");

        assertThat(reply.text())
            .contains("mora pendiente")
            .contains("75.000");            // x1000 + separador es-CO
        verify(sessionRepo).deleteById(PHONE);
    }

    @Test
    void opcionInvalidaReexplicaElMenuConDescripciones() {
        ChatSession session = new ChatSession(PHONE, Step.AWAITING_OPTION, "001", hace(30));
        when(sessionRepo.findById(PHONE)).thenReturn(Optional.of(session));

        Reply reply = service.handle(FROM_E164, "saldo");

        // El mensaje de error debe indicar que la opcion no es valida Y volver a
        // mostrar el menu con descripcion (no solo decir "1, 2 o 3" a secas).
        assertThat(reply.text())
            .contains("Opcion invalida")
            .contains("1. Saldo pendiente")
            .contains("2. Proxima cuota")
            .contains("3. Mora");
        assertThat(session.getStep()).isEqualTo(Step.AWAITING_OPTION);
        assertThat(session.getContractId()).isEqualTo("001");
        verify(clientRepo, never()).findById(any());
        verify(sessionRepo, times(1)).save(session);
        verify(sessionRepo, never()).deleteById(any(String.class));
    }

    // ---- helpers ----

    private static Instant hace(long segundos) {
        return Instant.now().minusSeconds(segundos);
    }
}
