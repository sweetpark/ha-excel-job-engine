package io.github.sweetpark.haexcel.storage;

import java.io.IOException;
import java.nio.file.Path;

/** Storage strategy provider interface for saving and retrieving generated Excel/ZIP files. */
public interface StorageProvider {

  StorageType getType();

  String storeFile(Path source, String key, String contentType) throws IOException;

  StorageResource getResource(String key) throws IOException;

  void delete(String key) throws IOException;
}
