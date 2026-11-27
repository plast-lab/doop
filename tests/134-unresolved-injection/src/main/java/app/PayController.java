package app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PayController {
    // Two Gateway beans, and the field's name is neither's id: Spring's choice
    // is not visible here.
    @Autowired private Gateway gateway;
    // No Clock bean in the analysed code.
    @Autowired private Clock clock;

    // The constructor writes both fields; Spring injects them afterwards.
    public PayController() { this.gateway = null; this.clock = null; }

    @GetMapping
    public String pay() { return gateway.pay() + clock.now(); }
}
