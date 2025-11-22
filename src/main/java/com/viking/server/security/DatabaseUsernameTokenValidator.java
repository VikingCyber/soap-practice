package com.viking.server.security;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.viking.server.repository.UserRepository;

@Component
public class DatabaseUsernameTokenValidator implements CallbackHandler {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUsernameTokenValidator.class);
    
    // ThreadLocal to store username for the current request
    private static final ThreadLocal<String> currentUsername = new ThreadLocal<>();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseUsernameTokenValidator(UserRepository userRepository,
                                         PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        try {
            for (Callback callback : callbacks) {
                if (callback instanceof WSPasswordCallback) {
                    WSPasswordCallback pc = (WSPasswordCallback) callback;
                    String username = pc.getIdentifier();
                    
                    if (username == null) {
                        throw new IOException("Username is required");
                    }
                    
                    // Store username in ThreadLocal for later retrieval
                    currentUsername.set(username);
                    logger.info("Stored username in ThreadLocal: {}", username);
                    
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
        } finally {
            // Note: We don't clear ThreadLocal here - it will be cleared after request processing
        }
    }
    
    /**
     * Get the username for the current thread (set during validation)
     */
    public static String getCurrentUsername() {
        return currentUsername.get();
    }
    
    /**
     * Clear the username for the current thread (should be called after request processing)
     */
    public static void clearCurrentUsername() {
        currentUsername.remove();
    }
}
