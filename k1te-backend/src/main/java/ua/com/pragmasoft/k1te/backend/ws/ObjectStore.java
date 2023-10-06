package ua.com.pragmasoft.k1te.backend.ws;

import java.net.URI;

public interface ObjectStore {
  URI store(String fileName, String fileType, long fileSize);
}
