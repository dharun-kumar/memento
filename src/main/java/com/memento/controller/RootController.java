package com.memento.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class RootController {

    // Redirect / → /auth/token.
    // If the user is logged in, they see their token page.
    // If not, Spring Security intercepts and redirects to /login automatically.
    @GetMapping("/")
    public RedirectView root() {
        return new RedirectView("/auth/token");
    }

}
