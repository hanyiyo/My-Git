package cn.medical.doctor;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DoctorHomeController {
    private final DoctorHomeService home;

    public DoctorHomeController(DoctorHomeService home) { this.home = home; }

    @GetMapping("/doctor/home")
    DoctorHomeService.HomeView ownHome(JwtAuthenticationToken authentication) {
        return home.ownHome(authentication.getName());
    }
}
