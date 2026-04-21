package com.memento.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    // Serves our own login page instead of relying on Spring Security's DefaultLoginPageGeneratingFilter.
    // loginPage("/login") in SecurityConfig points Spring Security here.
    // POST /login is still handled automatically by Spring Security's UsernamePasswordAuthenticationFilter.
    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model) {

        // Pass flags to the template — Thymeleaf uses th:if="${error}" / th:if="${logout}"
        model.addAttribute("error", error != null);
        model.addAttribute("logout", logout != null);

        return "login"; // resolves to src/main/resources/templates/login.html
    }

}
