/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

import java.net.URI;

public interface SendBinary extends SendText {

  URI uri();

  String fileName();

  String fileType();

  long fileSize();
}
