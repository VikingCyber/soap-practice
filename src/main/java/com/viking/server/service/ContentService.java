package com.viking.server.service;

import java.io.File;
import java.io.IOException;

import jakarta.activation.DataHandler;

public interface ContentService {
    File loadContent(String name);
    void storeContent(String name, DataHandler content) throws IOException;
}
