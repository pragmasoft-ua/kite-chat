/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.infrastructure.ddb;

import software.amazon.awssdk.enhanced.dynamodb.Key;

interface Keyed {
  Key key();
}
