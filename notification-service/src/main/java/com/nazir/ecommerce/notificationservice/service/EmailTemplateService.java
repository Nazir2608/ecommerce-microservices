package com.nazir.ecommerce.notificationservice.service;

import com.nazir.ecommerce.notificationservice.event.OrderEvent;
import com.nazir.ecommerce.notificationservice.event.PaymentEvent;
import com.nazir.ecommerce.notificationservice.event.UserEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;

/**
 * Email sender — Thymeleaf templates + JavaMailSender.
 *
 * LEARNING POINT — Thymeleaf for emails:
 *   Thymeleaf renders HTML templates from the classpath (templates/ folder).
 *   Variables injected via Context object are available in templates as ${var}.
 *   MimeMessageHelper enables HTML content (vs plain text SimpleMailMessage).
 *
 * LEARNING POINT — JavaMailSender:
 *   In dev: points to Mailhog (localhost:1025) — catches all emails, shows in UI
 *   In prod: points to AWS SES / SendGrid / SMTP server
 *   The code is identical — only application.yml changes.
 *   View sent emails: http://localhost:8025
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateService {

    private final JavaMailSender  mailSender;
    private final TemplateEngine  templateEngine;

    private static final String FROM = "no-reply@nazir-ecommerce.com";

    // ── User notifications ────────────────────────────────────────────────

    public void sendWelcomeEmail(UserEvent event) {
        Context ctx = new Context();
        ctx.setVariable("firstName",  event.getFirstName() != null ? event.getFirstName() : event.getUsername());
        ctx.setVariable("email",      event.getEmail());
        ctx.setVariable("loginUrl",   "http://localhost:8081/swagger-ui.html");

        send(event.getEmail(),
             "Welcome to Nazir E-Commerce! 🎉",
             "welcome-email", ctx);
    }

    public void sendAccountSuspendedEmail(UserEvent event) {
        Context ctx = new Context();
        ctx.setVariable("firstName", event.getFirstName() != null ? event.getFirstName() : event.getUsername());
        ctx.setVariable("supportEmail", "support@nazir-ecommerce.com");

        send(event.getEmail(),
             "Your account has been suspended",
             "account-suspended", ctx);
    }

    // ── Order notifications ───────────────────────────────────────────────

    public void sendOrderConfirmedEmail(OrderEvent event) {
        Context ctx = new Context();
        ctx.setVariable("orderNumber", event.getOrderNumber());
        ctx.setVariable("totalAmount", formatAmount(event.getTotalAmount(), event.getCurrency()));
        ctx.setVariable("trackingUrl", "http://localhost:8082/api/v1/orders/" + event.getOrderId());

        send(event.getUserEmail(),
             "Order Confirmed — " + event.getOrderNumber(),
             "order-confirmed", ctx);
    }

    public void sendOrderShippedEmail(OrderEvent event) {
        Context ctx = new Context();
        ctx.setVariable("orderNumber", event.getOrderNumber());
        ctx.setVariable("trackingUrl", "http://localhost:8082/api/v1/orders/" + event.getOrderId());

        send(event.getUserEmail(),
             "Your Order Has Shipped! 📦 — " + event.getOrderNumber(),
             "order-shipped", ctx);
    }

    public void sendOrderDeliveredEmail(OrderEvent event) {
        Context ctx = new Context();
        ctx.setVariable("orderNumber", event.getOrderNumber());

        send(event.getUserEmail(),
             "Order Delivered — " + event.getOrderNumber(),
             "order-delivered", ctx);
    }

    public void sendOrderCancelledEmail(OrderEvent event) {
        Context ctx = new Context();
        ctx.setVariable("orderNumber", event.getOrderNumber());
        ctx.setVariable("reason",      event.getReason() != null ? event.getReason() : "Requested cancellation");
        ctx.setVariable("totalAmount", formatAmount(event.getTotalAmount(), event.getCurrency()));

        send(event.getUserEmail(),
             "Order Cancelled — " + event.getOrderNumber(),
             "order-cancelled", ctx);
    }

    // ── Payment notifications ─────────────────────────────────────────────

    public void sendPaymentFailedEmail(PaymentEvent event) {
        Context ctx = new Context();
        ctx.setVariable("orderId",       event.getOrderId());
        ctx.setVariable("amount",        formatAmount(event.getAmount(), event.getCurrency()));
        ctx.setVariable("failureReason", event.getFailureReason() != null
                ? event.getFailureReason() : "Payment was declined");
        ctx.setVariable("retryUrl", "http://localhost:8082/api/v1/orders/" + event.getOrderId());

        send(event.getUserEmail(),
             "Payment Failed — Action Required",
             "payment-failed", ctx);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void send(String to, String subject, String templateName, Context ctx) {
        try {
            String html = templateEngine.process(templateName, ctx);

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(FROM);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true); // true = HTML

            mailSender.send(msg);
            log.info("[Email] SENT '{}' → {}", subject, to);

        } catch (MessagingException e) {
            log.error("[Email] FAILED to send '{}' → {}: {}", subject, to, e.getMessage());
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) return "N/A";
        String symbol = switch (currency != null ? currency : "USD") {
            case "INR" -> "₹";
            case "EUR" -> "€";
            case "GBP" -> "£";
            default    -> "$";
        };
        return symbol + String.format("%.2f", amount);
    }
}
