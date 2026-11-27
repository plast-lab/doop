package app;

import org.springframework.stereotype.Service;

@Service
public class StripeGateway implements Gateway { public String pay() { return "stripe"; } }
