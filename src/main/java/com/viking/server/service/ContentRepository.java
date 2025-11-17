package com.viking.server.service;

import java.io.File;
import java.io.IOException;

import com.viking.exception.ValidationException;

import jakarta.activation.DataHandler;

public interface ContentRepository {
    File loadContent(String name);
    void storeContent(String name, DataHandler content) throws IOException, ValidationException;
}
