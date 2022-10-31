package ua.com.pragmasoft.k1te.chat.infrastructure;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import ua.com.pragmasoft.k1te.chat.IdGenerator;

public class NanoIdGenerator implements IdGenerator {

  @Override
  public String randomStringId(int size) {
    return NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
        NanoIdUtils.DEFAULT_ALPHABET, size);
  }

}
