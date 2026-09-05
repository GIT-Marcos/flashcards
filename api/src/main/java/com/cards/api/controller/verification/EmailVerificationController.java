package com.cards.api.controller.verification;

import com.cards.api.config.properties.ApplicationProperties;
import com.cards.api.service.VerificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/auth")
public class EmailVerificationController {

    private final VerificationService verificationService;
    private final ApplicationProperties properties;

    public EmailVerificationController(VerificationService verificationService, ApplicationProperties properties) {
        this.verificationService = verificationService;
        this.properties = properties;
    }

    @GetMapping("/confirm")
    public String showConfirmPage(@RequestParam("token") String token, Model model) {
        try {
            verificationService.validateTokenStructure(token);
            model.addAttribute("token", token);
            return "confirm-email";
        } catch (Exception e) {
            model.addAttribute("message", "This verification link is invalid or has expired. Please sign up again.");
            return "email-verified";
        }
    }

    @PostMapping("/confirm")
    public String confirmEmail(@RequestParam("token") String token, Model model) {
        try {
            verificationService.confirmEmail(token);
            model.addAttribute("message", "Your email has been verified! You can now log in.");
            model.addAttribute("appUrl", properties.getNotifications().getAppUrl());
        } catch (Exception e) {
            model.addAttribute("message", "This verification link is invalid or has expired. Please sign up again.");
        }
        return "email-verified";
    }
}
