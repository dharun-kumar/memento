package com.memento.controller;

import com.memento.config.AppUserDetails;
import com.memento.config.JwtUtil;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // Session-protected — only reachable after logging in via /login.
    // Generates a JWT for the logged-in user and passes it to the Thymeleaf token template.
    // User copies the token and shares it with their AI agent.
    @GetMapping("/token")
    public String getToken(Authentication authentication, Model model) {
        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
        String token = jwtUtil.generate(userDetails.getId(), userDetails.getUsername(), userDetails.getRole());

        model.addAttribute("username", userDetails.getUsername());
        model.addAttribute("token", token);

        return "token"; // resolves to src/main/resources/templates/token.html
    }

}
