package com.example.auth.controller;

import com.example.auth.entity.User;
import com.example.auth.service.AccountLockService;
import com.example.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountLockService accountLockService;

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails currentUser, Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("currentUser", currentUser.getUsername());
        return "user-list";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "user-register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam(required = false) String nickname,
                           @RequestParam(required = false) String email,
                           @RequestParam(required = false) String phone,
                           Model model) {
        try {
            userService.register(username, password, nickname, email, phone);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("username", username);
            model.addAttribute("nickname", nickname);
            model.addAttribute("email", email);
            model.addAttribute("phone", phone);
            return "user-register";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        model.addAttribute("editUser", user);
        return "user-edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @RequestParam(required = false) String nickname,
                       @RequestParam(required = false) String email,
                       @RequestParam(required = false) String phone,
                       @RequestParam(required = false) String roles,
                       @RequestParam(required = false) Boolean enabled) {
        userService.update(id, nickname, email, phone, roles, enabled);
        return "redirect:/users";
    }

    @PostMapping("/{id}/password")
    public String changePassword(@PathVariable Long id,
                                 @RequestParam String newPassword) {
        userService.changePassword(id, newPassword);
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails currentUser) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (user.getUsername().equals(currentUser.getUsername())) {
            log.warn("用户 {} 尝试删除自己，已拒绝", currentUser.getUsername());
            return "redirect:/users?error=self";
        }
        userService.delete(id);
        return "redirect:/users";
    }

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id) {
        accountLockService.unlock(id);
        return "redirect:/users";
    }
}