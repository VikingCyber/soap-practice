package com.viking.server.security;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.viking.server.repository.UserRepository;

@Component
public class DatabaseUsernameTokenValidator implements CallbackHandler {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseUsernameTokenValidator(UserRepository userRepository,
                                         PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof WSPasswordCallback) {
                WSPasswordCallback pc = (WSPasswordCallback) callback;
                String username = pc.getIdentifier();
                
                if (username == null) {
                    throw new IOException("Username is required");
                }
                
                // Retrieve the stored password from the database
                String storedPassword = userRepository.findByUsername(username)
                        .map(u -> u.getPassword())
                        .orElse(null);
                
                if (storedPassword == null) {
                    throw new IOException("Invalid username or password");
                }
                
                // Set the stored password on the callback
                // WSS4J will compare the password from the SOAP message with this value
                // Since we're using NoOpPasswordEncoder (plain text), we can set it directly
                // If passwords were encoded, we'd need to decode first
                pc.setPassword(storedPassword);
            }
        }
    }
}
