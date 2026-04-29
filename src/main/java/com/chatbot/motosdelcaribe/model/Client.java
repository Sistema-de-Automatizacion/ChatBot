package com.chatbot.motosdelcaribe.model;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Snapshot read-only del cliente, deduplicado a la semana mas reciente del contrato.
 *
 * La vista subyacente db_packgps.vw_sv_all_motos_semanal guarda una fila por
 * contrato x semana (~8 semanas por contrato en data real). Si la mapearamos
 * con @Table simple, cualquier findById o JOIN inflaria los resultados N veces.
 * El @Subselect aplica el dedup MAX(fecha_semanal) GROUP BY contrato una sola
 * vez aqui, asi cualquier consumidor (findByPhone, findByPhoneAndNumPlate, etc.)
 * recibe automaticamente el snapshot semanal mas reciente.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Immutable
@Subselect("""
    SELECT v.*
    FROM vw_sv_all_motos_semanal v
    INNER JOIN (
        SELECT contrato, MAX(fecha_semanal) AS last_semana
        FROM vw_sv_all_motos_semanal
        WHERE fecha_semanal <= CURRENT_DATE
        GROUP BY contrato
    ) latest
        ON v.contrato      = latest.contrato
       AND v.fecha_semanal = latest.last_semana
    """)
@Synchronize("vw_sv_all_motos_semanal")
public class Client {

    @Id
    @Column(name = "contrato")
    private String contrato;

    @Column(name = "placa")
    private String numPlate;

    @Column(name = "arrendador")
    private String name;

    @Column(name = "saldo")
    private double balance;

    @Column(name = "cuota")
    private double payment;

    @Column(name = "deuda_cli")
    private double acummulatedDebet;

    @Column(name = "TELULT")
    private String phone;

    @Column(name = "estado_semana")
    private String paymentState;

    @Column(name = "dia_canon")
    private String paymentDay;

}
