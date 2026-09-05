package com.cards.api.controller.unsubscribe;

import com.cards.api.service.unsubscribe.UnsubscribeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller that serves an HTML confirmation page when the user clicks the
 * unsubscribe link from a notification email.
 * <p>
 * Unlike the rest of the API — which uses {@code @RestController} to serve JSON
 * to the SPA frontend — this endpoint is accessed directly from the user's email
 * client, not from the application UI. Returning HTML avoids requiring a dedicated
 * frontend route and keeps the unsubscribe flow self-contained on the API side.
 */
@Controller
@RequestMapping("/unsubscribe")
public class UnsubscribeController {

    private final UnsubscribeService unsubscribeService;

    public UnsubscribeController(UnsubscribeService unsubscribeService) {
        this.unsubscribeService = unsubscribeService;
    }

    @GetMapping
    public String unsubscribe(@RequestParam("token") String token, Model model) {
        try {
            unsubscribeService.unsubscribe(token);
            model.addAttribute("message", "You have been unsubscribed from review reminders.");
        } catch (Exception e) {
            model.addAttribute("message", "This unsubscribe link is invalid or has expired.");
        }
        return "unsubscribe-confirmation";
    }
}
