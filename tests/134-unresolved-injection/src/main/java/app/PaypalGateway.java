package app;

import org.springframework.stereotype.Service;

@Service
public class PaypalGateway implements Gateway { public String pay() { return "paypal"; } }
