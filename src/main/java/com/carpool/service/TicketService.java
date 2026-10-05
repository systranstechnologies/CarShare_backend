package com.carpool.service;

import com.carpool.dto.ApiResponse;
import com.carpool.entity.Ticket;
import com.carpool.entity.TicketCategory;
import com.carpool.entity.User;
import com.carpool.exception.AppException;
import com.carpool.repository.TicketCategoryRepository;
import com.carpool.repository.TicketRepository;
import com.carpool.security.AuthFacade;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository categoryRepository;
    private final AuthFacade authFacade;
    private final NotificationService notificationService;
    private final com.carpool.repository.UserRepository userRepository;
    private final AuditService auditService;

    public List<TicketCategory> categories() {
        List<TicketCategory> categories = new java.util.ArrayList<>(categoryRepository.findAll());
        addDefaultCategory(categories, "DOCUMENT_VERIFICATION", "Document verification", "ALL");
        addDefaultCategory(categories, "SAFETY_INCIDENT", "Safety incident (urgent)", "ALL");
        addDefaultCategory(categories, "RIDE_ISSUES", "Ride issues", "ALL");
        addDefaultCategory(categories, "BOOKING_ISSUES", "Booking issues", "ALL");
        addDefaultCategory(categories, "PAYMENT_ISSUES", "Payment issues", "ALL");
        addDefaultCategory(categories, "SUBSCRIPTION_ISSUES", "Subscription issues", "OWNER");
        addDefaultCategory(categories, "REFERRAL_ISSUES", "Referral issues", "ALL");
        addDefaultCategory(categories, "COIN_REDEMPTION", "Coin redemption", "ALL");
        addDefaultCategory(categories, "WALLET_ISSUES", "Wallet issues", "ALL");
        addDefaultCategory(categories, "TECHNICAL_ISSUES", "Technical issues", "ALL");
        return categories;
    }

    private void addDefaultCategory(List<TicketCategory> categories, String code, String label, String role) {
        if (categories.stream().noneMatch(category -> code.equals(category.getCode()))) {
            TicketCategory category = new TicketCategory();
            category.setCode(code);
            category.setLabel(label);
            category.setForRole(role);
            categories.add(category);
        }
    }

    @Transactional
    public Ticket create(MultipartFile image, String category, String description) {
        java.util.UUID userId = authFacade.currentUser().getUserId();
        User u = userRepository.findById(userId).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        String categoryCode = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        TicketCategory selectedCategory = categories().stream()
                .filter(candidate -> categoryCode.equals(candidate.getCode()))
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "Select a valid ticket category"));
        if (!"ALL".equalsIgnoreCase(selectedCategory.getForRole())
                && !u.getRole().name().equalsIgnoreCase(selectedCategory.getForRole())) {
            throw new AppException(HttpStatus.FORBIDDEN, "CATEGORY_NOT_ALLOWED", "This ticket category is not available for your role");
        }
        Ticket t = new Ticket();
        t.setUser(u);
        t.setCategory(categoryCode);
        t.setDescription(description);
        t.setPriority("SAFETY_INCIDENT".equals(categoryCode) ? "URGENT" : "NORMAL");
        if (image != null && !image.isEmpty()) {
            try {
                Path dir = Path.of("storage/tickets"); Files.createDirectories(dir);
                String filename = System.currentTimeMillis() + "_" + image.getOriginalFilename();
                Path dest = dir.resolve(filename);
                Files.copy(image.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
                t.setImagePath("/files/tickets/" + filename);
            } catch (IOException e) {
                throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "Unable to store image");
            }
        }
        Ticket saved = ticketRepository.save(t);
        auditService.log("TICKET_CREATED", u.getId().toString(), saved.getId().toString(), "category=" + categoryCode + ", priority=" + saved.getPriority());
        // notify submitter
        notificationService.create(u.getId(), com.carpool.entity.NotificationType.TICKET_RAISED, "Ticket raised", "Ticket #" + saved.getId() + " created.");
        // notify admins
        try {
            userRepository.findAll().stream().filter(us -> us.getRole() == com.carpool.entity.Role.ADMIN).forEach(admin -> {
                try {
                    String title = "URGENT".equals(saved.getPriority()) ? "Urgent safety incident" : "Ticket raised";
                    notificationService.create(admin.getId(), com.carpool.entity.NotificationType.TICKET_RAISED, title, "Ticket #" + saved.getId() + " created by " + u.getMobile());
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
        return saved;
    }

    public List<Ticket> myTickets() {
        java.util.UUID userId = authFacade.currentUser().getUserId();
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Ticket> adminList(String status) {
        if (authFacade.currentUser().getRole() != com.carpool.entity.Role.ADMIN) throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin only");
        return ticketRepository.findAll().stream()
            .filter(ticket -> status == null || status.isBlank() || ticket.getStatus().equalsIgnoreCase(status))
            .sorted(Comparator.comparing((Ticket ticket) -> !"URGENT".equals(ticket.getPriority()))
                .thenComparing(Ticket::getCreatedAt, Comparator.reverseOrder()))
            .toList();
    }

    public Ticket find(UUID id) { return ticketRepository.findById(id).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ticket not found")); }

    @Transactional
    public Ticket resolve(UUID id, String resolution) {
        Ticket t = find(id);
        t.setResolution(resolution);
        t.setStatus("RESOLVED");
        ticketRepository.save(t);
        auditService.log("TICKET_RESOLVED", authFacade.currentUser().getUserId().toString(), t.getId().toString(), "priority=" + t.getPriority());
        String message = "Your ticket #" + t.getId() + " is resolved.";
        if (resolution != null && !resolution.isBlank()) message += " Admin response: " + resolution;
        notificationService.create(t.getUser().getId(), com.carpool.entity.NotificationType.TICKET_RESOLVED, "Ticket resolved", message);
        return t;
    }
}
