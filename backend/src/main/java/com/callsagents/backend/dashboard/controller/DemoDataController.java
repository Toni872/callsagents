package com.callsagents.backend.dashboard.controller;

import com.callsagents.backend.appointments.entity.Appointment;
import com.callsagents.backend.appointments.entity.AppointmentStatus;
import com.callsagents.backend.appointments.repository.AppointmentRepository;
import com.callsagents.backend.calls.entity.Call;
import com.callsagents.backend.calls.entity.CallOutcome;
import com.callsagents.backend.calls.entity.CallStatus;
import com.callsagents.backend.calls.repository.CallRepository;
import com.callsagents.backend.campaigns.entity.Campaign;
import com.callsagents.backend.campaigns.entity.CampaignLead;
import com.callsagents.backend.campaigns.entity.CampaignLeadStatus;
import com.callsagents.backend.campaigns.entity.CampaignStatus;
import com.callsagents.backend.campaigns.repository.CampaignLeadRepository;
import com.callsagents.backend.campaigns.repository.CampaignRepository;
import com.callsagents.backend.leads.entity.Lead;
import com.callsagents.backend.leads.entity.LeadSource;
import com.callsagents.backend.leads.entity.LeadStatus;
import com.callsagents.backend.leads.repository.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DEV-ONLY controller that seeds the database with realistic demo data.
 *
 * Purpose:
 *  - The dashboard has data to show during development, demos, and stakeholder previews
 *  - Stakeholders can see Callsagents "in action" without manual data entry
 *
 * Called via: POST /api/admin/seed-demo-data (ADMIN only).
 * Idempotent: if leads or campaigns already exist, the seed is skipped.
 *
 * Cascade plan:
 *  1. Campaign (1)
 *  2. Leads (12, 8 assigned to admin, 4 unassigned)
 *  3. CampaignLeads (one per Lead, status=PENDING)
 *  4. Calls (8, referencing (campaign_id, lead_id) from CampaignLeads, user_id = admin)
 *  5. Appointments (3, lead_id from Leads, user_id = admin)
 *
 * Calling idempotently: the early guard prevents double-seed if the demo
 * was already injected.
 */
@RestController
@RequestMapping("/admin")
public class DemoDataController {

    private static final Logger log = LoggerFactory.getLogger(DemoDataController.class);

    private final LeadRepository leadRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignLeadRepository campaignLeadRepository;
    private final CallRepository callRepository;
    private final AppointmentRepository appointmentRepository;
    private final com.callsagents.backend.auth.repository.UserRepository userRepository;

    public DemoDataController(LeadRepository leadRepository,
                              CampaignRepository campaignRepository,
                              CampaignLeadRepository campaignLeadRepository,
                              CallRepository callRepository,
                              AppointmentRepository appointmentRepository,
                              com.callsagents.backend.auth.repository.UserRepository userRepository) {
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.campaignLeadRepository = campaignLeadRepository;
        this.callRepository = callRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/seed-demo-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SeedResult seedDemoData() {
        // Idempotency: if there's any data, skip.
        if (campaignRepository.count() > 0 || leadRepository.count() > 0) {
            log.info("Demo data already present (leads={}, campaigns={}) — skipping seed",
                leadRepository.count(), campaignRepository.count());
            return new SeedResult(leadRepository.count(), campaignRepository.count(),
                callRepository.count(), appointmentRepository.count(), false);
        }

        // Need a real user to set as agent / assigned_to / user_id (FK constraints).
        var admin = userRepository.findByEmail("admin@callsagents.local")
            .orElseThrow(() -> new IllegalStateException(
                "Admin seed user missing — V2__seed_admin.sql did not run"));
        UUID adminId = admin.getId();

        // 1. Campaign (id asignado automaticamente por @UuidGenerator)
        Campaign campaign = new Campaign();
        campaign.setName("Q4 Outbound — Empresas SaaS");
        campaign.setDescription("Campaña de outbound para empresas SaaS mid-market de Argentina");
        campaign.setStatus(CampaignStatus.RUNNING);
        campaign.setStartAt(Instant.now());
        campaign.setScript("Hola {{name}}, te llamo de Callsagents porque vimos que tu empresa...");
        campaign.setCreatedBy(adminId);
        campaign = campaignRepository.save(campaign);

        // 2. Leads (12 — 8 con admin asignado, 4 sin asignar)
        List<LeadSeed> leadSeeds = List.of(
            // firstName, lastName, email, phone, company, status, assigned
            new LeadSeed("Juan", "Pérez", "juan.perez@acme.com", "+5491112345601", "Acme Corp", LeadStatus.QUALIFIED, true),
            new LeadSeed("María", "García", "maria.garcia@globex.com", "+5491112345602", "Globex", LeadStatus.IN_PROGRESS, true),
            new LeadSeed("Carlos", "López", "carlos.lopez@initech.com", "+5491112345603", "Initech", LeadStatus.NEW, false),
            new LeadSeed("Lucía", "Martínez", "lucia.martinez@hooli.com", "+5491112345604", "Hooli", LeadStatus.QUALIFIED, true),
            new LeadSeed("Diego", "Rodríguez", "diego.rodriguez@piedpiper.com", "+5491112345605", "Pied Piper", LeadStatus.NEW, false),
            new LeadSeed("Sofía", "Fernández", "sofia.fernandez@vandelay.com", "+5491112345606", "Vandelay Industries", LeadStatus.IN_PROGRESS, true),
            new LeadSeed("Mateo", "Gómez", "mateo.gomez@starkind.com", "+5491112345607", "Stark Industries", LeadStatus.CONVERTED, true),
            new LeadSeed("Valentina", "Pérez", "valentina.perez@wayne.com", "+5491112345608", "Wayne Enterprises", LeadStatus.NOT_QUALIFIED, true),
            new LeadSeed("Joaquín", "Díaz", "joaquin.diaz@cyberdyne.com", "+5491112345609", "Cyberdyne", LeadStatus.DISQUALIFIED, false),
            new LeadSeed("Camila", "Romero", "camila.romero@tyrell.com", "+5491112345610", "Tyrell Corp", LeadStatus.NEW, false),
            new LeadSeed("Federico", "Alvarez", "federico.alvarez@oscorp.com", "+5491112345611", "Oscorp", LeadStatus.IN_PROGRESS, true),
            new LeadSeed("Isabella", "Torres", "isabella.torres@massivedynamic.com", "+5491112345612", "Massive Dynamic", LeadStatus.NEW, false)
        );

        List<Lead> leads = new ArrayList<>();
        for (LeadSeed s : leadSeeds) {
            Lead l = new Lead();
            l.setFirstName(s.firstName);
            l.setLastName(s.lastName);
            l.setEmail(s.email);
            l.setPhone(s.phone);
            l.setCompany(s.company);
            l.setStatus(s.status);
            l.setSource(LeadSource.MANUAL);
            l.setNotes("Lead de ejemplo (seed demo data)");
            if (s.assigned) {
                l.setAssignedTo(adminId);
            }
            l.setConsentAt(Instant.now());
            l.setDoNotCall(false);
            leads.add(l);
        }
        leads = leadRepository.saveAll(leads);

        // 3. CampaignLeads (1 por lead, status PENDING)
        List<CampaignLead> campaignLeads = new ArrayList<>();
        for (Lead l : leads) {
            CampaignLead cl = new CampaignLead();
            cl.setCampaignId(campaign.getId());
            cl.setLeadId(l.getId());
            cl.setStatus(CampaignLeadStatus.PENDING);
            cl.setAttempts(0);
            cl.setAssignedTo(l.getAssignedTo()); // inherit from Lead
            campaignLeads.add(cl);
        }
        campaignLeads = campaignLeadRepository.saveAll(campaignLeads);

        // 4. Calls (8 hoy — referencias válidas a CampaignLead)
        LocalDate today = LocalDate.now();
        Instant baseTime = today.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(9 * 3600);

        List<Call> calls = List.of(
            call(campaignLeads.get(0), CallStatus.CONNECTED, CallOutcome.INTERESTED, baseTime, adminId),
            call(campaignLeads.get(1), CallStatus.CONNECTED, CallOutcome.APPOINTMENT_SET, baseTime.plusSeconds(1800), adminId),
            call(campaignLeads.get(2), CallStatus.VOICEMAIL, null, baseTime.plusSeconds(3600), adminId),
            call(campaignLeads.get(3), CallStatus.CONNECTED, CallOutcome.CALLBACK, baseTime.plusSeconds(5400), adminId),
            call(campaignLeads.get(4), CallStatus.NO_ANSWER, null, baseTime.plusSeconds(7200), adminId),
            call(campaignLeads.get(5), CallStatus.CONNECTED, CallOutcome.INTERESTED, baseTime.plusSeconds(9000), adminId),
            call(campaignLeads.get(6), CallStatus.BUSY, null, baseTime.plusSeconds(10800), adminId),
            call(campaignLeads.get(7), CallStatus.CONNECTED, CallOutcome.NOT_INTERESTED, baseTime.plusSeconds(12600), adminId)
        );
        callRepository.saveAll(calls);

        // 5. Appointments (3 próximos, con FK válida a Lead y admin como user_id)
        LocalDateTime nextWeek = LocalDateTime.now().plusDays(3);
        List<Appointment> appointments = List.of(
            appointment(leads.get(0), nextWeek.with(LocalTime.of(10, 0)),
                AppointmentStatus.CONFIRMED, 30, adminId),
            appointment(leads.get(2), nextWeek.plusDays(1).with(LocalTime.of(15, 30)),
                AppointmentStatus.PENDING, 45, adminId),
            appointment(leads.get(5), nextWeek.plusDays(2).with(LocalTime.of(11, 0)),
                AppointmentStatus.PENDING, 30, adminId)
        );
        appointmentRepository.saveAll(appointments);

        long leadCount = leadRepository.count();
        long campaignCount = campaignRepository.count();
        long callCount = callRepository.count();
        long appointmentCount = appointmentRepository.count();
        log.info("Seeded demo data: {} leads, {} campaigns, {} calls, {} appointments",
            leadCount, campaignCount, callCount, appointmentCount);

        return new SeedResult(leadCount, campaignCount, callCount, appointmentCount, true);
    }

    public record SeedResult(
        long leads,
        long campaigns,
        long calls,
        long appointments,
        boolean seeded
    ) {}

    private record LeadSeed(
        String firstName, String lastName, String email, String phone,
        String company, LeadStatus status, boolean assigned
    ) {}

    private Call call(CampaignLead cl, CallStatus status, CallOutcome outcome, Instant createdAt, UUID userId) {
        Call c = new Call();
        c.setCampaignId(cl.getCampaignId());
        c.setLeadId(cl.getLeadId());
        c.setUserId(userId);
        c.setStartedAt(createdAt);
        c.setEndedAt(createdAt.plusSeconds(180));
        c.setDurationSeconds(180);
        c.setStatus(status);
        c.setOutcome(outcome);
        return c;
    }

    private Appointment appointment(Lead lead, LocalDateTime scheduledAt,
                                   AppointmentStatus status, int durationMinutes, UUID userId) {
        Appointment a = new Appointment();
        a.setLeadId(lead.getId());
        a.setUserId(userId);
        a.setScheduledAt(scheduledAt.toInstant(ZoneOffset.UTC));
        a.setDurationMinutes(durationMinutes);
        a.setStatus(status);
        a.setNotes("Reunión de seguimiento");
        return a;
    }
}
