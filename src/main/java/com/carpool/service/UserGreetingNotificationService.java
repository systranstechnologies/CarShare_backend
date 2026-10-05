package com.carpool.service;

import com.carpool.entity.NotificationType;
import com.carpool.entity.User;
import com.carpool.repository.NotificationRepository;
import com.carpool.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserGreetingNotificationService {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter HOLIDAY_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Value("${app.holidays:01-26=Republic Day,05-01=May Day,08-15=Independence Day,10-02=Gandhi Jayanti,12-25=Christmas}")
    private String configuredHolidays;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void sendDailyGreetings() {
        LocalDate today = LocalDate.now(INDIA);
        for (User user : userRepository.findAll()) {
            if (!user.isActive()) continue;
            if (user.getDateOfBirth() != null && user.getDateOfBirth().getMonth() == today.getMonth()
                && user.getDateOfBirth().getDayOfMonth() == today.getDayOfMonth()) {
                createOncePerDay(user, NotificationType.BIRTHDAY_GREETING, "Happy Birthday, " + user.getFullName() + "!",
                    "Wishing you a wonderful birthday from carShare.");
            }
            String holidayName = holidays().get(today.format(HOLIDAY_FORMAT));
            if (holidayName != null) {
                createOncePerDay(user, NotificationType.GOVERNMENT_HOLIDAY_GREETING, "Happy " + holidayName + "!",
                    "carShare wishes you a happy and safe holiday.");
            }
        }
    }

    private void createOncePerDay(User user, NotificationType type, String title, String body) {
        Instant start = LocalDate.now(INDIA).atStartOfDay(INDIA).toInstant();
        Instant end = LocalDate.now(INDIA).plusDays(1).atStartOfDay(INDIA).toInstant();
        if (!notificationRepository.existsByUserIdAndTypeAndCreatedAtBetween(user.getId(), type, start, end)) {
            notificationService.create(user.getId(), type, title, body, "/");
        }
    }

    private Map<String, String> holidays() {
        return Arrays.stream(configuredHolidays.split(","))
            .map(String::trim)
            .filter(value -> value.contains("="))
            .map(value -> value.split("=", 2))
            .filter(parts -> parts.length == 2 && parts[0].matches("\\d{2}-\\d{2}") && !parts[1].isBlank())
            .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1].trim(), (first, second) -> first));
    }
}