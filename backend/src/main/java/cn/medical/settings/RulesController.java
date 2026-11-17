package cn.medical.settings;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RulesController {
    private final RulesService rules;
    public RulesController(RulesService rules) { this.rules=rules; }
    @GetMapping({"/settings/rules","/admin/settings/rules"}) RulesService.Rules current() { return rules.current(); }
    @PutMapping("/admin/settings/rules") RulesService.Rules save(@Valid @RequestBody RulesService.Rules input) { return rules.save(input); }
}
